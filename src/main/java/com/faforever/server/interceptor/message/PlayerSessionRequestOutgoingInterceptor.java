package com.faforever.server.interceptor.message;

import com.faforever.server.interceptor.message.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.ReplyToRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@Singleton
@Identifier("player-session-request")
public class PlayerSessionRequestOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public PlayerSessionRequestOutgoingInterceptor(
            @ConfigProperty(name = "mp.messaging.incoming.player-session-state.queue.name") String queueName) {
        super(List.of(new ReplyToRabbitMQOutgoingInterceptor(queueName)));
    }

}
