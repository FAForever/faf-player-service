package com.faforever.server.social;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.LobbyMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.service.InboundLobbyMessage;
import com.faforever.server.message.service.OutboundLobbyMessage;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.micrometer.InboundMessageCommandResolver;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.aop.MeterTag;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@ApplicationScoped
@RunOnVirtualThread
class SocialHandler {

    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;
    private final Emitter<PlayerAvatarUpdate> outboundAvatarUpdateEmitter;

    private final SocialRepository socialRepository;

    SocialHandler(
            @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
            @Channel("avatar-updates-out") Emitter<PlayerAvatarUpdate> outboundAvatarUpdateEmitter,
            SocialRepository socialRepository) {
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.outboundAvatarUpdateEmitter = outboundAvatarUpdateEmitter;
        this.socialRepository = socialRepository;
    }

    @Incoming("social-inbound")
    @Timed(value = "faf.request", extraTags = {"type", "social"}, histogram = true)
    @MDCPopulated
    void handleSocialRequest(@MeterTag(value = "command",
                                              resolver = InboundMessageCommandResolver.class) InboundLobbyMessage<SocialMessage.Client> request) {
        int playerId = request.playerId();
        switch (request.message()) {
            case SocialMessage.ListAvatarsRequest(String requestId) -> {
                LOG.info("Listing avatars");
                Set<AvatarInfo> avatars = socialRepository.findAssignedAvatarsByPlayer(playerId)
                                                                  .stream()
                                                          .map(avatar -> new AvatarInfo(avatar.getUrl(),
                                                                                        avatar.getDescription()))
                                                                  .collect(Collectors.toSet());
                SocialMessage.AvatarInfoList avatarInfoList = new SocialMessage.AvatarInfoList(requestId, avatars);
                outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(request.sessionId(), avatarInfoList));
            }
            case SocialMessage.SelectAvatarRequest(_, String avatarUrl) -> {
                LOG.info("Selecting avatar {}", avatarUrl);
                int avatarId = socialRepository.updateSelectedAvatar(playerId, avatarUrl);
                outboundAvatarUpdateEmitter.send(new PlayerAvatarUpdate(playerId, avatarId));
            }
            case SocialMessage.RemoveAvatarRequest _ -> {
                LOG.info("Removing avatar");
                socialRepository.removeSelectedAvatar(playerId);
                outboundAvatarUpdateEmitter.send(new PlayerAvatarUpdate(playerId, null));
            }
            case SocialMessage.SocialAddRequest(_, Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    LOG.info("Adding friend {}", friendId);
                    addSocialRelationship(playerId, friendId, FriendOrFoeEntity.Status.FRIEND);
                }
                if (foeId != null) {
                    LOG.info("Adding foe {}", foeId);
                    addSocialRelationship(playerId, foeId, FriendOrFoeEntity.Status.FOE);
                }
            }
            case SocialMessage.SocialRemoveRequest(_, Integer friendId, Integer foeId) -> {
                if (friendId != null) {
                    LOG.info("Removing friend {}", friendId);
                    removeSocialRelationship(playerId, friendId);
                }
                if (foeId != null) {
                    LOG.info("Removing foe {}", foeId);
                    removeSocialRelationship(playerId, foeId);
                }
            }
        }
    }

    private void addSocialRelationship(int playerId, int targetId, FriendOrFoeEntity.Status status) {
        socialRepository.upsertPlayerRelationship(
                new FriendOrFoeEntity(new FriendOrFoeEntity.Id(playerId, targetId), status));
    }

    private void removeSocialRelationship(int playerId, int targetId) {
        socialRepository.deletePlayerRelationship(new FriendOrFoeEntity.Id(playerId, targetId));
    }

}
