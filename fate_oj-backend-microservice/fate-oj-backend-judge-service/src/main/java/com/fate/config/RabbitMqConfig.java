package com.fate.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 判题消息队列配置
 */
@Configuration
public class RabbitMqConfig {

    public static final String CODE_EXCHANGE = "code_exchange";

    public static final String CODE_QUEUE = "code_queue";

    public static final String CODE_ROUTING_KEY = "fate_code";

    @Bean
    public DirectExchange codeExchange() {
        return new DirectExchange(CODE_EXCHANGE, true, false);
    }

    @Bean
    public Queue codeQueue() {
        return new Queue(CODE_QUEUE, true);
    }

    @Bean
    public Binding codeBinding(Queue codeQueue, DirectExchange codeExchange) {
        return BindingBuilder.bind(codeQueue).to(codeExchange).with(CODE_ROUTING_KEY);
    }
}
