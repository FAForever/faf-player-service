package com.faforever.server.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class Player {

    @Getter
    private final int id;
    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String username;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String country = "";
    private @Nullable String clan;
    private @Nullable Avatar avatar;

    private final Map<String, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();

    @Getter
    private State state = State.ONLINE;

    Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    void markOffline() {
        state = State.OFFLINE;
    }

    void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    Set<LeaderboardRating> getLeaderboardRatings() {
        return Set.copyOf(leaderboardRatings.values());
    }

    void setClan(String clan) {
        if (Objects.equals(clan, this.clan)) {
            return;
        }

        this.clan = clan;
    }

    @Nullable String getClan() {
        return clan;
    }

    void setAvatar(Avatar avatar) {
        if (Objects.equals(avatar, this.avatar)) {
            return;
        }

        this.avatar = avatar;
    }

    void clearAvatar() {
        if (this.avatar == null) {
            return;
        }

        this.avatar = null;
    }

    @Nullable Avatar getAvatar() {
        return avatar;
    }

    enum State {
        ONLINE, OFFLINE
    }
}
