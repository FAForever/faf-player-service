package com.faforever.server.interceptor.message.util;

import io.smallrye.reactive.messaging.OutgoingInterceptor;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import org.eclipse.microprofile.reactive.messaging.Message;

//Used to ensure all rabbitmq messages have a routing key
public class RoutingKeyRabbitMQOutgoingInterceptor implements OutgoingInterceptor {

    private final String routingKey;

    public RoutingKeyRabbitMQOutgoingInterceptor(String routingKey) {
        this.routingKey = routingKey;
    }

    @Override
    public void onMessageAck(Message<?> message) {

    }

    @Override
    public void onMessageNack(Message<?> message, Throwable failure) {

    }

    @Override
    public Message<?> beforeMessageSend(Message<?> message) {
        OutgoingRabbitMQMetadata newMetadata = message.getMetadata(OutgoingRabbitMQMetadata.class)
                                                      .map(OutgoingRabbitMQMetadata::from)
                                                      .orElseGet(OutgoingRabbitMQMetadata::builder)
                                                      .withRoutingKey(routingKey)
                                                      .build();
        return message.addMetadata(newMetadata);
    }
}
