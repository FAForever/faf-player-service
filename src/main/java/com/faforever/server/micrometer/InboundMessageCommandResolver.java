package com.faforever.server.micrometer;

import com.faforever.server.message.service.InboundLobbyMessage;
import io.micrometer.common.annotation.ValueResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
public class InboundMessageCommandResolver implements ValueResolver {
    @Override
    public String resolve(@Nullable Object parameter) {
        return switch (parameter) {
            case InboundLobbyMessage<?> message -> message.message().command();
            case null, default -> "";
        };
    }
}