package com.faforever.server.player;

import com.faforever.server.domain.UserGroupAssignment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class UserGroupAssignmentRepository implements PanacheRepositoryBase<UserGroupAssignment, Integer> {

    @Transactional
    boolean playerLacksPermission(int playerId, String permission) {
        return count("userId = ?1 and userGroup.groupPermissions.technicalName = ?1", playerId, permission) == 0;
    }

    @Transactional
    boolean isPlayerModerator(int playerId) {
        return count("userId = ?1 and userGroup.technicalName = 'faf_moderators_global'", playerId) > 0;
    }

}
