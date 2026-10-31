package com.faforever.server.player;

import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

@Repository
interface UserGroupAssignmentRepository {

    @Transactional
    @Query("select case when count(u) > 0 then false else true end from UserGroupAssignment u join u.userGroup.groupPermissions p where u.userId = :playerId and p.technicalName = :permission")
    boolean playerLacksPermission(@Param("playerId") int playerId, @Param("permission") String permission);

    @Transactional
    @Query("select case when count(u) > 0 then true else false end from UserGroupAssignment u where u.userId = :playerId and u.userGroup.technicalName = 'faf_moderators_global'")
    boolean isPlayerModerator(int playerId);

}
