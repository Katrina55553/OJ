package com.oj.mq;

import com.oj.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 判题消息生产者
 */
@Slf4j
@Component
public class JudgeMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送判题消息
     *
     * @param questionSubmitId 提交记录 ID
     */
    public void sendJudgeMessage(Long questionSubmitId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.JUDGE_EXCHANGE,
                RabbitMQConfig.JUDGE_ROUTING_KEY,
                questionSubmitId,
                message -> {
                    // 消息持久化
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return message;
                }
        );
        log.info("判题消息已发送: questionSubmitId={}", questionSubmitId);
    }
}
