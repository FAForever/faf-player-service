package com.faforever.server.player;

import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
interface PlayerStateRepository {

    @Query("""
           select new Player(p.id, p.name, c.tag, a.url, a.description)
           from PlayerEntity p
           left join p.selectedAvatar a
           left join p.clan c
           where p.id = :playerId
           """)
    Optional<Player> loadPlayer(int playerId);

    @Query("""
           select new FriendOrFoe(fof.status, fof.id.subjectId)
           from FriendOrFoeEntity fof
           where fof.id.playerId = :playerId
           """)
    List<FriendOrFoe> loadFriendsAndFoes(int playerId);

    @Query("select case when count(u) > 0 then true else false end from UserGroupAssignmentEntity u where u.userId = :playerId and u.userGroup.technicalName = 'faf_moderators_global'")
    boolean isPlayerModerator(int playerId);

    @Query("""
           select new PlayerLeaderboardRating(lr.playerId, new com.faforever.server.player.LeaderboardRating(lr.leaderboard.technicalName, lr.totalGames, lr.mean, lr.deviation))
           from LeaderboardRatingEntity lr
           where lr.playerId in :playerIds and lr.updateTime >= :timeCutoff
           """)
    List<PlayerLeaderboardRating> loadUpdatedLeaderboardRatings(Collection<Integer> playerIds, Instant timeCutoff);

    @Query("""
           select new LeaderboardRating(lr.leaderboard.technicalName, lr.totalGames, lr.mean, lr.deviation)
           from LeaderboardRatingEntity lr
           where lr.playerId = :playerId
           """)
    List<LeaderboardRating> loadPlayerRatings(int playerId);

    @Query("""
           select new Avatar(a.url, a.description)
           from AvatarEntity a
           where a.id = :avatarId
           """)
    Optional<Avatar> findAvatar(int avatarId);

    @Query("update PlayerEntity p set p.ip = :ipAddress where p.id = :playerId")
    void updatePlayerIpAddress(int playerId, String ipAddress);
}
