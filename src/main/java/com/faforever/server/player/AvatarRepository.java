package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class AvatarRepository implements PanacheRepositoryBase<AvatarEntity, Integer> {

    @Transactional
    Avatar findAvatar(int avatarId) {
        return find("id", avatarId).singleResultOptional().map(Avatar::from).orElseThrow();
    }
}
