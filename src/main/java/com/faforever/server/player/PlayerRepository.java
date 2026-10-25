package com.faforever.server.player;

import com.faforever.server.domain.AvatarEntity;
import com.faforever.server.domain.PlayerEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
class PlayerRepository implements PanacheRepositoryBase<PlayerEntity, Integer> {

    @Transactional
    Player loadPlayer(int playerId) {
        return findByIdOptional(playerId).map(Player::from).orElseThrow(() -> new IllegalStateException("No user with id %d".formatted(playerId)));
    }

    @Transactional
    void removePlayerAvatar(int playerId) {
        update("set avatar = null where player.id = ?2", playerId);
    }

    @Transactional
    void updatePlayerAvatar(int playerId, AvatarEntity avatar) {
        update("set avatar = ?1 where player.id = ?2", avatar, playerId);
    }

}
