package com.resume.screening.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.queue.hr-notification}")
    private String hrNotificationQueue;

    @Value("${app.rabbitmq.routing-key.hr-notification}")
    private String hrNotificationRoutingKey;

    @Bean
    public TopicExchange resumeScreeningExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public Queue hrNotificationQueue() {
        return QueueBuilder.durable(hrNotificationQueue)
                .withArgument("x-message-ttl", 86400000)   // 24h TTL
                .build();
    }

    @Bean
    public Binding hrNotificationBinding() {
        return BindingBuilder
                .bind(hrNotificationQueue())
                .to(resumeScreeningExchange())
                .with(hrNotificationRoutingKey);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}