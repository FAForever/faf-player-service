package com.faforever.server.player;

import com.faforever.server.domain.PlayerEntity;
import jakarta.data.repository.By;
import jakarta.data.repository.Find;
import jakarta.data.repository.Param;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.transaction.Transactional;

import java.util.Optional;

@Repository
interface PlayerRepository {

    @Find
    @Transactional
    Optional<PlayerEntity> loadPlayer(@By(By.ID) int id);

    @Transactional
    @Query("update PlayerEntity p set p.ip = :ipAddress where p.id = :playerId")
    void updatePlayerIpAddress(@Param("playerId") int playerId, @Param("ipAddress") String ipAddress);
}
