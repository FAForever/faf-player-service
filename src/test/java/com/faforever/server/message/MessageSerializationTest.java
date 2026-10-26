package com.faforever.server.message;

import com.faforever.server.message.client.AdminMessage;
import com.faforever.server.message.client.ConnectionMessage;
import com.faforever.server.message.client.SocialMessage;
import com.faforever.server.message.client.dto.AvatarInfo;
import com.faforever.server.message.client.dto.LeaderboardStats;
import com.faforever.server.message.client.dto.PlayerInfo;
import com.faforever.server.message.service.PlayerAvatarUpdate;
import com.faforever.server.message.service.PlayerSessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageSerializationTest {

    private JsonMapper jsonMapper;

    @BeforeEach
    void setup() {
        jsonMapper = JsonMapper.builder()
                               .propertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy())
                               .build();
    }

    @Test
    void testNoticeInfo() throws Exception {
        AdminMessage.NoticeInfo message = new AdminMessage.NoticeInfo("test", AdminMessage.Style.INFO);

        JSONAssert.assertEquals("""
                {
                    "command": "notice",
                    "message": "test",
                    "style": "info"
                }
                """, jsonMapper.writeValueAsString((AdminMessage.Server) message), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testLoginSuccessResponse() throws Exception {
        PlayerInfo playerInfo = new PlayerInfo(
                1,
                "test",
                "TEST",
                new AvatarInfo("http://avatar.url", "My Avatar"),
                "DE",
                Map.of("global", new LeaderboardStats(5, new LeaderboardStats.Rating(1500.0, 50.0))),
                PlayerInfo.State.OFFLINE
        );
        OffsetDateTime now = OffsetDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneOffset.UTC);
        ConnectionMessage.LoginSuccessResponse message = new ConnectionMessage.LoginSuccessResponse(playerInfo, now);

        JSONAssert.assertEquals("""
                {
                    "command": "welcome",
                    "me": {
                        "id": 1,
                        "login": "test",
                        "clan": "TEST",
                        "avatar": {
                            "url": "http://avatar.url",
                            "tooltip": "My Avatar"
                        },
                        "country": "DE",
                        "ratings": {
                            "global": {
                                "number_of_games": 5,
                                "rating": [1500.0, 50.0]
                            }
                        },
                        "state": "offline"
                    },
                    "current_time": "2026-10-24T12:00:00Z"
                }
                """, jsonMapper.writeValueAsString((ConnectionMessage.Server) message), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPlayerInfoList() throws Exception {
        PlayerInfo playerInfo = new PlayerInfo(
                1,
                "test",
                "TEST",
                new AvatarInfo("http://avatar.url", "My Avatar"),
                "DE",
                Map.of("global", new LeaderboardStats(5, new LeaderboardStats.Rating(1500.0, 50.0))),
                null
        );
        SocialMessage.PlayerInfoList message = new SocialMessage.PlayerInfoList(Set.of(playerInfo));

        JSONAssert.assertEquals("""
                {
                    "command": "player_info",
                    "players": [
                        {
                            "id": 1,
                            "login": "test",
                            "clan": "TEST",
                            "avatar": {
                                "url": "http://avatar.url",
                                "tooltip": "My Avatar"
                            },
                            "country": "DE",
                            "ratings": {
                                "global": {
                                    "number_of_games": 5,
                                    "rating": [1500.0, 50.0]
                                }
                            },
                            "state": null
                        }
                    ]
                }
                """, jsonMapper.writeValueAsString((SocialMessage.Server) message), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testAvatarInfoList() throws Exception {
        SocialMessage.AvatarInfoList message = new SocialMessage.AvatarInfoList(
                "req-123",
                Set.of(new AvatarInfo("http://avatar.url", "My Avatar"))
        );

        JSONAssert.assertEquals("""
                {
                    "command": "avatar",
                    "request_id": "req-123",
                    "avatarlist": [
                        {
                            "url": "http://avatar.url",
                            "tooltip": "My Avatar"
                        }
                    ]
                }
                """, jsonMapper.writeValueAsString((SocialMessage.Server) message), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testSocialInfo() throws Exception {
        SocialMessage.SocialInfo message = new SocialMessage.SocialInfo(
                List.of("#aeolus", "#clan"),
                List.of(2, 3),
                List.of(4, 5)
        );

        JSONAssert.assertEquals("""
                {
                    "command": "social",
                    "autojoin": ["#aeolus", "#clan"],
                    "friends": [2, 3],
                    "foes": [4, 5]
                }
                """, jsonMapper.writeValueAsString((SocialMessage.Server) message), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testBroadcastRequest() {
        String json = """
                {
                    "command": "admin",
                    "request_id": "req-1",
                    "action": "broadcast",
                    "message": "Server restart"
                }
                """;
        AdminMessage.BroadcastRequest expected = new AdminMessage.BroadcastRequest("req-1", "Server restart");
        assertEquals(expected, jsonMapper.readValue(json, AdminMessage.Client.class));
    }

    @Test
    void testClosePlayerGameRequest() {
        String json = """
                {
                    "command": "admin",
                    "request_id": "req-2",
                    "action": "closeFA",
                    "user_id": 42
                }
                """;
        AdminMessage.ClosePlayerGameRequest expected = new AdminMessage.ClosePlayerGameRequest("req-2", 42);
        assertEquals(expected, jsonMapper.readValue(json, AdminMessage.Client.class));
    }

    @Test
    void testKickPlayerRequest() {
        String json = """
                {
                    "command": "admin",
                    "request_id": "req-3",
                    "action": "closelobby",
                    "user_id": 42
                }
                """;
        AdminMessage.KickPlayerRequest expected = new AdminMessage.KickPlayerRequest("req-3", 42);
        assertEquals(expected, jsonMapper.readValue(json, AdminMessage.Client.class));
    }

    @Test
    void testSocialAddRequest() {
        String json = """
                {
                    "command": "social_add",
                    "request_id": "req-4",
                    "friend": 42,
                    "foe": null
                }
                """;
        SocialMessage.SocialAddRequest expected = new SocialMessage.SocialAddRequest("req-4", 42, null);
        assertEquals(expected, jsonMapper.readValue(json, SocialMessage.Client.class));
    }

    @Test
    void testSocialRemoveRequest() {
        String json = """
                {
                    "command": "social_remove",
                    "request_id": "req-5",
                    "friend": null,
                    "foe": 42
                }
                """;
        SocialMessage.SocialRemoveRequest expected = new SocialMessage.SocialRemoveRequest("req-5", null, 42);
        assertEquals(expected, jsonMapper.readValue(json, SocialMessage.Client.class));
    }

    @Test
    void testListAvatarsRequest() {
        String json = """
                {
                    "command": "avatar",
                    "request_id": "req-6",
                    "action": "list_avatar"
                }
                """;
        SocialMessage.ListAvatarsRequest expected = new SocialMessage.ListAvatarsRequest("req-6");
        assertEquals(expected, jsonMapper.readValue(json, SocialMessage.Client.class));
    }

    @Test
    void testSelectAvatarRequest() {
        String json = """
                {
                    "command": "avatar",
                    "request_id": "req-7",
                    "action": "select",
                    "avatar": "http://avatar.url/avatar.png"
                }
                """;
        SocialMessage.SelectAvatarRequest expected = new SocialMessage.SelectAvatarRequest("req-7", "http://avatar.url/avatar.png");
        assertEquals(expected, jsonMapper.readValue(json, SocialMessage.Client.class));
    }

    @Test
    void testRemoveAvatarRequest() {
        String json = """
                {
                    "command": "avatar",
                    "request_id": "req-8",
                    "action": "select",
                    "avatar": null
                }
                """;
        SocialMessage.RemoveAvatarRequest expected = new SocialMessage.RemoveAvatarRequest("req-8");
        assertEquals(expected, jsonMapper.readValue(json, SocialMessage.Client.class));
    }

    @Test
    void testPlayerAvatarUpdate() throws Exception{
        String json = """
                {
                    "player_id": 1,
                    "avatar_id": null
                }
                """;
        PlayerAvatarUpdate expected = new PlayerAvatarUpdate(1, null);
        assertEquals(expected, jsonMapper.readValue(json, PlayerAvatarUpdate.class));
        JSONAssert.assertEquals(json, jsonMapper.writeValueAsString(expected), JSONCompareMode.NON_EXTENSIBLE);
    }

    @Test
    void testPlayerSessionState() {
        String newJson = """
                {
                    "player_id": 1,
                    "session_id": 2,
                             "ip_address": "127.0.0.1",
                    "state": {
                      "@type": "new",
                      "seconds_to_expiry": 120
                    }
                }
                """;
        PlayerSessionState expectedNew = new PlayerSessionState(1, 2, "127.0.0.1", new PlayerSessionState.State.New(
                Duration.ofMinutes(2)));
        assertEquals(expectedNew, jsonMapper.readValue(newJson, PlayerSessionState.class));

        String existingJson = """
                {
                    "player_id": 1,
                    "session_id": 2,
                                  "ip_address": "127.0.0.1",
                    "state": {
                      "@type": "existing",
                      "seconds_to_expiry": 120
                    }
                }
                """;
        PlayerSessionState expectedExisting = new PlayerSessionState(1, 2, "127.0.0.1",
                                                                     new PlayerSessionState.State.Existing(
                                                                             Duration.ofMinutes(2)));
        assertEquals(expectedExisting, jsonMapper.readValue(existingJson, PlayerSessionState.class));

        String goneJson = """
                {
                    "player_id": 1,
                    "session_id": 2,
                              "ip_address": "127.0.0.1",
                    "state": {
                      "@type": "gone"
                    }
                }
                """;
        PlayerSessionState expectedGone = new PlayerSessionState(1, 2, "127.0.0.1",
                                                                 new PlayerSessionState.State.Gone());
        assertEquals(expectedGone, jsonMapper.readValue(goneJson, PlayerSessionState.class));
    }
}
