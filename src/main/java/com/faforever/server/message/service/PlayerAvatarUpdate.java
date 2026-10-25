package com.faforever.server.message.service;

import org.jspecify.annotations.Nullable;

public record PlayerAvatarUpdate(int playerId, @Nullable Integer avatarId) {}
