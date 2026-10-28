package com.faforever.server.message.client;

import com.faforever.server.message.service.LobbySchemaDetails;
import com.faforever.server.message.service.LobbySchemaDetails.Direction;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusComponentTest
public class LobbySchemaRegistrarTest {

    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12);

    @Inject
    LobbySchemaRegistrar lobbySchemaRegistrar;

    @BeforeEach
    void setup() {
        lobbySchemaRegistrar.registerSchemasAndSendSchemas();
    }

    @Test
    void testRegisteredSchemas() {
        Set<String> registered = lobbySchemaRegistrar.getLobbySchemaDetails()
                                                     .stream()
                                                     .map(details -> key(details.direction(), details.route(),
                                                                         details.command()))
                                                     .collect(Collectors.toSet());

        assertEquals(
                Set.of(key(Direction.OUTBOUND, "admin", "notice"), key(Direction.OUTBOUND, "connection", "welcome"),
                       key(Direction.OUTBOUND, "social", "player_info"), key(Direction.OUTBOUND, "social", "avatar"),
                       key(Direction.OUTBOUND, "social", "social"), key(Direction.INBOUND, "admin", "admin"),
                       key(Direction.INBOUND, "social", "social_add"),
                       key(Direction.INBOUND, "social", "social_remove"), key(Direction.INBOUND, "social", "avatar")),
                registered);
    }

    @Test
    void testBroadcastRequest() {
        assertValid("admin", "admin", """
                                      {
                                          "command": "admin",
                                          "request_id": "req-1",
                                          "action": "broadcast",
                                          "message": "Server restart"
                                      }
                                      """);
    }

    @Test
    void testClosePlayerGameRequest() {
        assertValid("admin", "admin", """
                                      {
                                          "command": "admin",
                                          "request_id": "req-2",
                                          "action": "closeFA",
                                          "user_id": 42
                                      }
                                      """);
    }

    @Test
    void testKickPlayerRequest() {
        assertValid("admin", "admin", """
                                      {
                                          "command": "admin",
                                          "request_id": "req-3",
                                          "action": "closelobby",
                                          "user_id": 42
                                      }
                                      """);
    }

    @Test
    void testAdminRequestRejectsMissingRequestId() {
        assertInvalid("admin", "admin", """
                                        {
                                            "command": "admin",
                                            "action": "broadcast",
                                            "message": "missing request id"
                                        }
                                        """);
    }

    @Test
    void testAdminRequestRejectsUnknownAction() {
        assertInvalid("admin", "admin", """
                                        {
                                            "command": "admin",
                                            "request_id": "req-1",
                                            "action": "unknown"
                                        }
                                        """);
    }

    @Test
    void testAdminRequestRejectsAdditionalProperty() {
        assertInvalid("admin", "admin", """
                                        {
                                            "command": "admin",
                                            "request_id": "req-1",
                                            "action": "broadcast",
                                            "message": "test",
                                            "unexpected": true
                                        }
                                        """);
    }

    @Test
    void testSocialAddRequest() {
        assertValid("social", "social_add", """
                                            {
                                                "command": "social_add",
                                                "request_id": "req-4",
                                                "friend": 42,
                                                "foe": null
                                            }
                                            """);
    }

    @Test
    void testSocialRemoveRequest() {
        assertValid("social", "social_remove", """
                                               {
                                                   "command": "social_remove",
                                                   "request_id": "req-5",
                                                   "friend": null,
                                                   "foe": 42
                                               }
                                               """);
    }

    @Test
    void testListAvatarsRequest() {
        assertValid("social", "avatar", """
                                        {
                                            "command": "avatar",
                                            "request_id": "req-6",
                                            "action": "list_avatar"
                                        }
                                        """);
    }

    @Test
    void testSelectAvatarRequest() {
        assertValid("social", "avatar", """
                                        {
                                            "command": "avatar",
                                            "request_id": "req-7",
                                            "action": "select",
                                            "avatar": "http://avatar.url/avatar.png"
                                        }
                                        """);
    }

    @Test
    void testRemoveAvatarRequest() {
        assertValid("social", "avatar", """
                                        {
                                            "command": "avatar",
                                            "request_id": "req-8",
                                            "action": "select",
                                            "avatar": null
                                        }
                                        """);
    }

    @Test
    void testAvatarRequestRejectsWrongCommand() {
        assertInvalid("social", "avatar", """
                                          {
                                              "command": "social_add",
                                              "request_id": "req-1",
                                              "action": "list_avatar"
                                          }
                                          """);
    }

    private void assertValid(String route, String command, String json) {
        Schema schema = getSchema(route, command);

        List<Error> errors = schema.validate(json, InputFormat.JSON);

        assertTrue(errors.isEmpty(), () -> "Schema validation failed: " + errors + "\nSchema: " + schema);
    }

    private void assertInvalid(String route, String command, String json) {
        Schema schema = getSchema(route, command);

        List<Error> errors = schema.validate(json, InputFormat.JSON);

        assertFalse(errors.isEmpty(), () -> "Schema validation unexpectedly passed\nSchema: " + schema);
    }

    private Schema getSchema(String route, String command) {
        LobbySchemaDetails schemaDetails = lobbySchemaRegistrar.getLobbySchemaDetails()
                                                               .stream()
                                                               .filter(details -> details.direction() ==
                                                                                  Direction.INBOUND)
                                                               .filter(details -> details.route().equals(route))
                                                               .filter(details -> details.command().equals(command))
                                                               .findFirst()
                                                               .orElseThrow(() -> new AssertionError(
                                                                       "No schema registered for " +
                                                                       key(Direction.INBOUND, route, command)));
        return SCHEMA_REGISTRY.getSchema(schemaDetails.schema());
    }

    private static String key(Direction direction, String route, String command) {
        return direction + "/" + route + "/" + command;
    }
}
