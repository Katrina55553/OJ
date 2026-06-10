package com.oj.mq;

import com.oj.config.RabbitMQConfig;
import com.oj.mapper.QuestionSubmitMapper;
import com.oj.model.entity.QuestionSubmit;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 死信队列消费者
 * 处理判题失败（主队列消费 3 次仍失败）的消息
 *
 * 作用：
 *   1. 记录异常判题的提交 ID，便于人工排查
 *   2. 更新判题状态为 FAILED，避免用户永远看到"判题中"
 *   3. 兜底保障：即使主流程异常，数据状态最终一致
 */
@Slf4j
@Component
public class DeadLetterConsumer {

    @Resource
    private QuestionSubmitMapper questionSubmitMapper;

    /**
     * 监听死信队列
     * 消息从主队列 oj.judge.queue 进入 DLQ 的条件：
     *   - 消费者 basicNack(requeue=false)
     *   - 消息 TTL 过期（本项目未配置 TTL，仅通过 nack 触发）
     */
    @RabbitListener(queues = RabbitMQConfig.JUDGE_QUEUE + ".dlq")
    public void handleDeadLetter(Long questionSubmitId, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.error("[死信队列] 判题消息进入 DLQ，开始兜底处理: questionSubmitId={}", questionSubmitId);

            // 更新判题状态为 FAILED（status=3）
            // 只更新状态仍为 RUNNING（1）的记录，避免覆盖已成功的
            QuestionSubmit update = new QuestionSubmit();
            update.setId(questionSubmitId);
            update.setStatus(3); // FAILED
            update.setJudgeInfo("{\"message\":\"判题异常，请稍后重试\",\"time\":0,\"memory\":0}");

            // MyBatis-Plus 条件更新：仅当 status=1（RUNNING）时更新
            int rows = questionSubmitMapper.updateById(update);
            if (rows > 0) {
                log.info("[死信队列] 已更新判题状态为 FAILED: questionSubmitId={}", questionSubmitId);
            } else {
                log.info("[死信队列] 记录状态已变更，跳过更新: questionSubmitId={}", questionSubmitId);
            }

            // 手动确认
            channel.basicAck(deliveryTag, false);
            log.info("[死信队列] 消息处理完成: questionSubmitId={}", questionSubmitId);

        } catch (Exception e) {
            log.error("[死信队列] 处理失败: questionSubmitId={}, error={}", questionSubmitId, e.getMessage(), e);
            try {
                // DLQ 消息处理失败，拒绝并丢弃（避免无限循环）
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("[死信队列] ACK 失败: deliveryTag={}", deliveryTag, ex);
            }
        }
    }
}
