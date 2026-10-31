package com.faforever.server.message.client.dto;

import com.faforever.server.player.Avatar;
import com.faforever.server.player.Player;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.stream.Collectors;

public record PlayerInfo(
        int id,
        String login,
        @Nullable String clan,
        @Nullable AvatarInfo avatar,
        String country,
        Map<String, LeaderboardStats> ratings,
        @Nullable State state
) {

    public enum State {
        @JsonProperty("offline") OFFLINE
    }

    public static PlayerInfo from(Player player) {
        Map<String, LeaderboardStats> leaderboardStatsMap = player.getLeaderboardRatings()
                                                                  .stream()
                                                                  .map(leaderboardRating -> {
                                                                      LeaderboardStats leaderboardStats = LeaderboardStats.from(
                                                                              leaderboardRating);
                                                                      return Map.entry(leaderboardRating.leaderboard(),
                                                                                       leaderboardStats);
                                                                  })
                                                                  .collect(Collectors.toMap(Map.Entry::getKey,
                                                                                            Map.Entry::getValue));
        State state = switch (player.getState()) {
            case ONLINE -> null;
            case OFFLINE -> State.OFFLINE;
        };
        String clan = player.getClan();
        Avatar playerAvatar = player.getAvatar();
        AvatarInfo avatar = playerAvatar == null ? null : AvatarInfo.from(playerAvatar);
        return new PlayerInfo(player.getId(), player.getUsername(), clan, avatar, player.getCountry(),
                              leaderboardStatsMap, state);
    }

}
