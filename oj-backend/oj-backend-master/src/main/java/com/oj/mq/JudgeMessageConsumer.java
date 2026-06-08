package com.oj.mq;

import com.oj.config.RabbitMQConfig;
import com.oj.judge.JudgeService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * 判题消息消费者
 */
@Slf4j
@Component
public class JudgeMessageConsumer {

    @Resource
    private JudgeService judgeService;

    /**
     * 处理判题消息
     */
    @RabbitListener(queues = RabbitMQConfig.JUDGE_QUEUE)
    public void handleJudgeMessage(Long questionSubmitId, Channel channel, Message message) {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("开始判题: questionSubmitId={}", questionSubmitId);
            judgeService.doJudge(questionSubmitId);
            // 手动确认消息
            channel.basicAck(deliveryTag, false);
            log.info("判题完成: questionSubmitId={}", questionSubmitId);
        } catch (Exception e) {
            log.error("判题失败: questionSubmitId={}, error={}", questionSubmitId, e.getMessage(), e);
            try {
                // 拒绝消息，不重新入队（进入死信队列）
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ex) {
                log.error("消息确认失败: deliveryTag={}", deliveryTag, ex);
            }
        }
    }
}
