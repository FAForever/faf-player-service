package com.faforever.server.interceptor.message;

import com.faforever.server.interceptor.message.util.CompundRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.PersistentRabbitMQOutgoingInterceptor;
import com.faforever.server.interceptor.message.util.RoutingKeyRabbitMQOutgoingInterceptor;
import io.smallrye.common.annotation.Identifier;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
@Identifier("avatar-updates-out")
public class AvatarUpdatesOutgoingInterceptor extends CompundRabbitMQOutgoingInterceptor {

    public AvatarUpdatesOutgoingInterceptor() {
        super(List.of(new PersistentRabbitMQOutgoingInterceptor(), new RoutingKeyRabbitMQOutgoingInterceptor("success.player_avatar.update")));
    }
}
