package com.faforever.server.micrometer;

import com.faforever.server.message.service.PlayerSessionState;
import io.micrometer.common.annotation.ValueResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
public class PlayerSessionStateResolver implements ValueResolver {
    @Override
    public String resolve(@Nullable Object parameter) {
        return switch (parameter) {
            case PlayerSessionState(_, _, PlayerSessionState.State.New _) -> "new";
            case PlayerSessionState(_, _, PlayerSessionState.State.Existing _) -> "existing";
            case PlayerSessionState(_, _, PlayerSessionState.State.Gone _) -> "gone";
            case null, default -> "";
        };
    }
}
