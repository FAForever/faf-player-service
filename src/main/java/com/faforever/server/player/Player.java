package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import com.faforever.server.rating.LeaderboardRating;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Player {

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

    private Player(int id, String username) {
        this.id = id;
        this.username = username;
    }

    static Player from(PlayerEntity playerEntity) {
        Player player = new Player(playerEntity.getId(), playerEntity.getName());
        if (playerEntity.getSelectedAvatar() != null) {
            player.setAvatar(Avatar.from(playerEntity.getSelectedAvatar()));
        }
        if (playerEntity.getClan() != null) {
            player.setClan(playerEntity.getClan().getTag());
        }
        return player;
    }

    void markOffline() {
        state = State.OFFLINE;
    }

    void updateLeaderboardRatings(Collection<LeaderboardRating> leaderboardRatings) {
        for (LeaderboardRating leaderboardRating : leaderboardRatings) {
            this.leaderboardRatings.put(leaderboardRating.leaderboard(), leaderboardRating);
        }
    }

    public Set<LeaderboardRating> getLeaderboardRatings() {
        return Set.copyOf(leaderboardRatings.values());
    }

    void setClan(String clan) {
        if (Objects.equals(clan, this.clan)) {
            return;
        }

        this.clan = clan;
    }

    public @Nullable String getClan() {
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

    public @Nullable Avatar getAvatar() {
        return avatar;
    }

    public enum State {
        ONLINE, OFFLINE
    }
}
