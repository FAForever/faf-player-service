package com.faforever.server.interceptor.cdi;

import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.service.InboundLobbyMessage;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

@MDCPopulated
@Priority(0)
@Interceptor
public class MDCInterceptor {

    public static final String COMMAND = "command";
    public static final String REQUEST_ID = "requestId";
    public static final String PLAYER_ID = "playerId";
    public static final String SESSION_ID = "sessionId";

    @AroundInvoke
    Object logInvocation(InvocationContext context) throws Exception {
        boolean hasMessage = false;
        boolean hasSession = false;

        @Nullable Object[] parameters = context.getParameters();
        if (parameters.length == 1) {
            switch (parameters[0]) {
                case InboundLobbyMessage<?>(
                        long sessionId, int playerId, LobbyMessage.Client message
                ) -> {
                    hasMessage = true;
                    MDC.put(REQUEST_ID, message.requestId());
                    MDC.put(COMMAND, message.command());
                    hasSession = true;
                    MDC.put(SESSION_ID, String.valueOf(sessionId));
                    MDC.put(PLAYER_ID, String.valueOf(playerId));
                }
                case null, default -> {}
            }
        }

        try {
            return context.proceed();
        } finally {
            if (hasSession) {
                MDC.remove(SESSION_ID);
                MDC.remove(PLAYER_ID);
            }
            if (hasMessage) {
                MDC.remove(COMMAND);
                MDC.remove(REQUEST_ID);
            }
        }
    }

}
