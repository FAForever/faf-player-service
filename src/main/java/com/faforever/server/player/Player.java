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
    private final String username;
    @Getter
    private final @Nullable String clan;

    @Getter
    @Setter(AccessLevel.PACKAGE)
    private String country = "";
    @Getter
    private @Nullable Avatar avatar;

    private final Map<String, LeaderboardRating> leaderboardRatings = new ConcurrentHashMap<>();

    Player(int id, String username, @Nullable String clan, @Nullable String avatarUrl,
           @Nullable String avatarDescription) {
        this.id = id;
        this.username = username;
        this.clan = clan;
        this.avatar = avatarUrl != null && avatarDescription != null ? new Avatar(avatarUrl, avatarDescription) : null;
    }

    void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    Set<LeaderboardRating> getLeaderboardRatings() {
        return Set.copyOf(leaderboardRatings.values());
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
}
