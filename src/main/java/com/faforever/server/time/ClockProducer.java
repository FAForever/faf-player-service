package com.faforever.server.time;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.time.Clock;

@ApplicationScoped
class ClockProducer {

    @Produces
    Clock clock() {
        return Clock.systemUTC();
    }


}
