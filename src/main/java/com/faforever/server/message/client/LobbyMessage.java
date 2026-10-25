package com.faforever.server.message.client;

public sealed interface LobbyMessage permits LobbyMessage.Client, LobbyMessage.Server {

    String command();

    sealed interface Server extends LobbyMessage permits AdminMessage.Server, ConnectionMessage.Server, Broadcast, SocialMessage.Server {}

    sealed interface Client extends LobbyMessage permits AdminMessage.Client, SocialMessage.Client {

        String requestId();

    }

    sealed interface Broadcast extends Server permits AdminMessage.NoticeInfo, SocialMessage.PlayerInfoList {}

}
