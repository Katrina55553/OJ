package com.oj.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置
 */
@Configuration
public class RabbitMQConfig {

    /** 判题队列 */
    public static final String JUDGE_QUEUE = "oj.judge.queue";
    /** 判题交换机 */
    public static final String JUDGE_EXCHANGE = "oj.judge.exchange";
    /** 判题路由键 */
    public static final String JUDGE_ROUTING_KEY = "oj.judge";
    /** 死信交换机 */
    public static final String DLX_EXCHANGE = "oj.judge.dlx";
    /** 死信路由键 */
    public static final String DLQ_ROUTING_KEY = "oj.judge.dlq";

    // ==================== 判题队列 ====================

    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(JUDGE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(JUDGE_EXCHANGE, true, false);
    }

    @Bean
    public Binding judgeBinding(Queue judgeQueue, DirectExchange judgeExchange) {
        return BindingBuilder.bind(judgeQueue).to(judgeExchange).with(JUDGE_ROUTING_KEY);
    }

    // ==================== 死信队列（处理失败的消息） ====================

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(JUDGE_QUEUE + ".dlq").build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ_ROUTING_KEY);
    }

    // ==================== 消息转换器 ====================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ==================== 消费者容器工厂 ====================

    /**
     * 配置消费者容器工厂，使用 JSON 消息转换器
     * 确保 @RabbitListener 能正确反序列化 JSON → Long/String/Object
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        // 消费者手动 ACK，与 application.yml 的 acknowledge-mode: manual 配合
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
