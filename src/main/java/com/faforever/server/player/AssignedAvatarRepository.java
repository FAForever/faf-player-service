package com.faforever.server.player;

import com.faforever.server.domain.AssignedAvatarEntity;
import com.faforever.server.domain.AvatarEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@ApplicationScoped
class AssignedAvatarRepository implements PanacheRepositoryBase<AssignedAvatarEntity, Integer> {

    private final PlayerRepository playerRepository;

    @Transactional
    Set<Avatar> findAssignedAvatarsByPlayer(int playerId) {
        return stream("where playerId = ?1 and (expirationDate is null or expirationDate < current_timestamp)", playerId)
                .map(AssignedAvatarEntity::getAvatar)
                .map(Avatar::from)
                .collect(Collectors.toSet());
    }

    @Transactional
    Avatar updateSelectedAvatar(int playerId, String avatarUrl) {
        update("set selected = (avatar.url = ?1) where playerId = ?2", avatarUrl, playerId);
        AvatarEntity avatar = find(
                "where playerId = ?1 and avatar.url = ?2 and (expirationDate is null or expirationDate < current_timestamp)",
                playerId, avatarUrl).singleResult().getAvatar();
        playerRepository.updatePlayerAvatar(playerId, avatar);
        return Avatar.from(avatar);
    }

    @Transactional
    void removeSelectedAvatar(int playerId) {
        update("set selected = false where playerId = ?1", playerId);
        playerRepository.removePlayerAvatar(playerId);
    }

}
