package com.faforever.server.player;

import com.faforever.server.domain.FriendOrFoeEntity;
import com.faforever.server.interceptor.cdi.MDCPopulated;
import com.faforever.server.message.client.AdminMessage;
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
public class PlayerService {

    private final Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter;
    private final Emitter<PlayerAvatarUpdate> outboundAvatarUpdateEmitter;

    private final FriendOrFoeRepository friendOrFoeRepository;
    private final AssignedAvatarRepository assignedAvatarRepository;
    private final UserGroupAssignmentRepository userGroupAssignmentRepository;

    PlayerService(
            @Channel("lobby-outbound") Emitter<OutboundLobbyMessage<LobbyMessage.Server>> outboundLobbyMessageEmitter,
            @Channel("avatar-updates-out") Emitter<PlayerAvatarUpdate> outboundAvatarUpdateEmitter,
            FriendOrFoeRepository friendOrFoeRepository, AssignedAvatarRepository assignedAvatarRepository,
            UserGroupAssignmentRepository userGroupAssignmentRepository) {
        this.outboundLobbyMessageEmitter = outboundLobbyMessageEmitter;
        this.outboundAvatarUpdateEmitter = outboundAvatarUpdateEmitter;
        this.friendOrFoeRepository = friendOrFoeRepository;
        this.assignedAvatarRepository = assignedAvatarRepository;
        this.userGroupAssignmentRepository = userGroupAssignmentRepository;
    }

    @Incoming("admin-inbound")
    @MDCPopulated
    @Timed(value = "faf.admin.request", histogram = true)
    public void handleAdminRequest(@MeterTag(value = "command",
                                             resolver = InboundMessageCommandResolver.class) InboundLobbyMessage<AdminMessage.Client> request) {
        switch (request.message()) {
            case AdminMessage.BroadcastRequest broadcastRequest -> broadcast(request.playerId(), broadcastRequest);
            case AdminMessage.KickPlayerRequest kickPlayerRequest -> kickPlayer(request.playerId(), kickPlayerRequest);
            case AdminMessage.ClosePlayerGameRequest closePlayerGameRequest -> closePlayerGame(request.playerId(),
                                                                                               closePlayerGameRequest);
        }
    }

    private void broadcast(int playerId, AdminMessage.BroadcastRequest broadcastRequest) {
        String message = broadcastRequest.message();
        if (message.isBlank()) {
            LOG.warn("Received broadcast with blank message");
            return;
        }

        if (userGroupAssignmentRepository.playerLacksPermission(playerId, "ADMIN_BROADCAST_MESSAGE")) {
            LOG.warn("Unauthorized broadcast request: {}", broadcastRequest.message());
            return;
        }

        LOG.info("Sending broad cast message: {}", message);
        outboundLobbyMessageEmitter.send(
                OutboundLobbyMessage.forAll(new AdminMessage.NoticeInfo(message, AdminMessage.Style.INFO)));
    }

    private void kickPlayer(int playerId, AdminMessage.KickPlayerRequest kickPlayerRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(playerId, "ADMIN_KICK_SERVER")) {
            LOG.warn("Unauthorized kick request with target: {}", kickPlayerRequest.playerId());
            return;
        }

        LOG.info("Kicking player `{}`", kickPlayerRequest.playerId());
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(kickPlayerRequest.playerId(),
                                                                        new AdminMessage.NoticeInfo(null,
                                                                                                    AdminMessage.Style.KICK)));
    }

    private void closePlayerGame(int playerId, AdminMessage.ClosePlayerGameRequest closePlayerGameRequest) {
        if (userGroupAssignmentRepository.playerLacksPermission(playerId, "ADMIN_KICK_SERVER")) {
            LOG.warn("Unauthorized close request with target: {}", closePlayerGameRequest.playerId());
            return;
        }

        LOG.info("Closing lobby for player `{}`", closePlayerGameRequest.playerId());
        outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forPlayer(closePlayerGameRequest.playerId(),
                                                                        new AdminMessage.NoticeInfo(null,
                                                                                                    AdminMessage.Style.KILL)));
    }

    @Incoming("social-inbound")
    @Timed(value = "faf.social.request", histogram = true)
    @MDCPopulated
    public void handleSocialRequest(@MeterTag(value = "command",
                                              resolver = InboundMessageCommandResolver.class) InboundLobbyMessage<SocialMessage.Client> request) {
        int playerId = request.playerId();
        switch (request.message()) {
            case SocialMessage.ListAvatarsRequest(String requestId) -> {
                LOG.info("Listing avatars");
                Set<AvatarInfo> avatars = assignedAvatarRepository.findAssignedAvatarsByPlayer(playerId).stream().map(
                        AvatarInfo::from).collect(Collectors.toSet());
                SocialMessage.AvatarInfoList avatarInfoList = new SocialMessage.AvatarInfoList(requestId, avatars);
                outboundLobbyMessageEmitter.send(OutboundLobbyMessage.forSession(request.sessionId(), avatarInfoList));
            }
            case SocialMessage.SelectAvatarRequest(_, String avatarUrl) -> {
                LOG.info("Selecting avatar {}", avatarUrl);
                Avatar avatar = assignedAvatarRepository.updateSelectedAvatar(playerId, avatarUrl);
                outboundAvatarUpdateEmitter.send(new PlayerAvatarUpdate(playerId, avatar.id()));
            }
            case SocialMessage.RemoveAvatarRequest _ -> {
                LOG.info("Removing avatar");
                assignedAvatarRepository.removeSelectedAvatar(playerId);
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
        friendOrFoeRepository.upsertPlayerRelationship(playerId, targetId, status);
    }

    private void removeSocialRelationship(int playerId, int targetId) {
        friendOrFoeRepository.deletePlayerRelationship(playerId, targetId);
    }

}
