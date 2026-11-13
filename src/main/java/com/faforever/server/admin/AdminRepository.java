package com.faforever.server.admin;

import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

@Repository
interface AdminRepository {

    @Transactional
    @Query("select case when count(u) > 0 then false else true end from UserGroupAssignmentEntity u join u.userGroup.groupPermissions p where u.userId = :playerId and p.technicalName = :permission")
    boolean playerLacksPermission(@Param("playerId") int playerId, @Param("permission") String permission);

}
