# FAF Player Service

[![Build Status](https://github.com/FAForever/faf-player-service/actions/workflows/build.yaml/badge.svg)](https://github.com/FAForever/faf-player-service/actions/workflows/build.yaml)

The **FAF Player Service** is a reactive backend microservice within
the [Forged Alliance Forever (FAF)](https://www.faforever.com/) ecosystem. It is primarily responsible for **tracking
player state** (online status, active sessions, leaderboard ratings, avatars, country, and social relationships) and
**communicating real-time state updates to clients** by publishing and forwarding messages over **RabbitMQ**.

---

## Table of Contents

- [Core Responsibilities](#core-responsibilities)
- [Architecture & State Tracking](#architecture--state-tracking)
  - [Player State Lifecycle](#player-state-lifecycle)
  - [Player Country Detection](#player-country-detection)
  - [Session Synchronization](#session-synchronization)
  - [Periodic Rating Synchronization](#periodic-rating-synchronization)
  - [Batch State Propagation (Dirty Player Updates)](#batch-state-propagation-dirty-player-updates)
- [RabbitMQ Messaging & Client Communication](#rabbitmq-messaging--client-communication)
  - [Inbound Channels](#inbound-channels)
  - [Outbound Channels](#outbound-channels)
  - [Message Routing & Targeting](#message-routing--targeting)
  - [Schema Registration](#schema-registration)
- [Supported Features](#supported-features)
  - [Social & Relationship Management](#social--relationship-management)
  - [Avatar Selection](#avatar-selection)
  - [Administration & Moderation](#administration--moderation)
- [Technology Stack](#technology-stack)
- [Configuration](#configuration)
  - [Environment Variables](#environment-variables)
  - [Database Version Check](#database-version-check)
- [Building & Running](#building--running)
  - [Prerequisites](#prerequisites)
  - [Development Mode](#development-mode)
  - [Running Tests](#running-tests)
  - [Packaging & Docker](#packaging--docker)

---

## Core Responsibilities

1. **Player State Tracking**: Manages in-memory player state across multiple concurrent sessions per player, tracking
   online/offline status, clan affiliation, selected avatar, country, and rating statistics across all leaderboards.
2. **Client State Communication**: Distributes player info, initial connection state, and incremental player state changes to connected game/lobby clients via RabbitMQ message exchanges.
3. **Social & Moderation Services**: Handles player friend/foe relationship updates, avatar listing/selection, and
   administrative actions (broadcast notices, closing a player's lobby client, and closing a player's game).
4. **Dynamic Schema Registration**: Generates and distributes JSON schemas for all client and server lobby messages upon startup to ensure strict contract compatibility across FAF microservices and clients.

---

## Architecture & State Tracking

The player service operates as an event-driven service powered by **Quarkus** and **SmallRye Reactive Messaging**.

```
                           +----------------------+
                           |   FAF Lobby Server   |
                           +----------+-----------+
                                      |
                           RabbitMQ (Session & Client Events)
                                      |
                                      v
+----------------------------------------------------------------------------------+
|                               FAF Player Service                                 |
|                                                                                  |
|   +-----------------------+     Updates     +--------------------------------+   |
|   |  PlayerSessionState   | --------------> |       PlayerStateTracker       |   |
|   |  (new/existing/gone)  |                 |  - playerIdMap                 |   |
|   +-----------------------+                 |  - sessionPlayerIdMap          |   |
|                                             |  - playerIdSessionMap          |   |
|   +-----------------------+                 |  - sessionExpirationMap        |   |
|   |   Avatar / Ratings    | --------------> |  - dirtyPlayerInfoIdMap        |   |
|   |       Updates         |                 +----------------+---------------+   |
|   +-----------------------+                                  | Batched (1s)      |
|                                                              v                   |
|   +-----------------------+     Dispatches   +-------------------------------+   |
|   |    SocialHandler /    | -------------->  | Outbound Lobby Message Queue  |   |
|   |     AdminHandler      |                  | (lobby-message-outbound)      |   |
|   +-----------------------+                  +---------------+---------------+   |
+--------------------------------------------------------------|-------------------+
                                                               |
                                                               v
                                                    +--------------------+
                                                    |  RabbitMQ Exchange |
                                                    | (to Lobby Clients) |
                                                    +--------------------+
```

### Player State Lifecycle

The `PlayerStateTracker` maintains concurrent in-memory maps of online players and active sessions:

- **`playerIdMap`**: Maps unique player IDs to online `Player` domain objects.
- **`sessionPlayerIdMap`**: Maps active connection session IDs to their player ID.
- **`playerIdSessionMap`**: Tracks all active session IDs associated with a player (supporting multi-session / reconnect
  scenarios).
- **`sessionExpirationMap`**: Tracks the expiry instant of each session to automatically discard abandoned or stale
  connections.
- **`dirtyPlayerInfoIdMap`**: Holds a `PlayerInfo` snapshot for every player whose state changed since the last
  broadcast.

When a player's first session is registered, the player is loaded from the database along with their leaderboard
ratings, their IP address is stored, and their country is resolved from that IP.

When all sessions for a player disconnect or expire, a `PlayerInfo` snapshot with state `OFFLINE` is recorded as dirty
and the player is removed from memory; the offline update is then broadcast to other clients by the next batch.

The sizes of these maps are exposed as Micrometer gauges (`faf.players` with `state=online|dirty`, `faf.sessions` with
`state=online|expired`).

### Player Country Detection

This application uses the [GeoLite2](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data) Country database to
identify a player's country (ISO code) from their IP address. The database is read from `GEO_IP_DATABASE_PATH`
(default `/geoip/GeoLite2-Country.mmdb`) and reloaded every 10 minutes, so it can be replaced without a restart.
If the file is not present, all players will have an empty country.

### Session Synchronization

- **Startup Recovery**: On service startup, `PlayerStateTracker` sends a session inquiry over `player-session-request`
  (`lobby-session-request` exchange), with `reply-to` set to this instance's `player-session-state` queue, so the lobby
  gateway reports all currently active client sessions.
- **Session Registration**: Listens on `player-session-state` (`lobby-session-state` exchange) for:
    - `new`: A new client connection. Sends the session its welcome messages (`welcome` / `LoginSuccessResponse`,
      `social` / `SocialInfo`, and a full `player_info` / `PlayerInfoList` of all online players) and marks the player
      as dirty for broadcast.
    - `existing`: Registers or refreshes a session and its expiration window without sending welcome messages.
  - `gone`: Disassociates a session; marks the player offline if no other sessions remain active.
- **Session Expiration**: A scheduled task runs every minute (`removeExpiredSessions`) to clean up sessions that exceeded their time-to-live.

### Periodic Rating Synchronization

A scheduled task executes every minute (`updatePlayerLeaderboardRatings`) to query MariaDB for any rating changes (`mean`, `deviation`, `totalGames`) across all leaderboards that occurred since the last evaluation timestamp for online players. Players with updated ratings are marked dirty.

### Batch State Propagation (Dirty Player Updates)

To minimize network traffic and avoid flooding clients with high-frequency updates, player modifications (avatar
changes, rating updates, login/logout events) mark the affected player as dirty by storing a `PlayerInfo` snapshot of
their current state.

A scheduled task runs every second (`sendUpdateForDirtyPlayers`):

1. Takes an immutable snapshot of the dirty `PlayerInfo` entries.
2. Publishes them as a single `player_info` (`SocialMessage.PlayerInfoList`) broadcast message to
   `lobby-message-outbound`.
3. Removes the sent entries from the dirty set.

---

## RabbitMQ Messaging & Client Communication

The service uses RabbitMQ via SmallRye Reactive Messaging to ingest client commands and forward responses.

### Inbound Channels

All inbound queues are exclusive, auto-delete queues suffixed with the instance ID (`faf.instance-id`, from `HOSTNAME`).

| Channel                | Exchange                | Queue                                  | Routing Key                    | Description                                                          |
|:-----------------------|:------------------------|:---------------------------------------|:-------------------------------|:---------------------------------------------------------------------|
| `player-session-state` | `lobby-session-state`   | `player.session.state.${instance-id}`  | –                              | Ingests session lifecycle events (`new`, `existing`, `gone`).        |
| `social-inbound`       | `lobby-message-inbound` | `player.message.social.${instance-id}` | `lobby.social.#`               | Client social requests (`social_add`, `social_remove`, `avatar`).    |
| `admin-inbound`        | `lobby-message-inbound` | `player.message.admin.${instance-id}`  | `lobby.admin.#`                | Privileged `admin` requests (`broadcast`, `closelobby`, `closeFA`).  |
| `avatar-updates`       | `faf-lobby`             | `player.avatar.updates.${instance-id}` | `success.player_avatar.update` | Avatar selection changes from any player service instance.           |
| `lobby-schema-request` | `lobby-schema-request`  | `player.schema.request.${instance-id}` | –                              | Requests to resend the JSON message schemas to the `reply-to` queue. |

### Outbound Channels

| Channel                     | Exchange                 | Description                                                                                                                |
|:----------------------------|:-------------------------|:---------------------------------------------------------------------------------------------------------------------------|
| `lobby-outbound`            | `lobby-message-outbound` | Outbound messages destined for clients (targeted by session, player ID, or broadcast).                                     |
| `lobby-schema-registration` | `lobby-schema-register`  | Publishes generated JSON schemas for client and server commands.                                                           |
| `player-session-request`    | `lobby-session-request`  | Requests an active session dump from the lobby gateway during startup (`reply-to` is this instance's session state queue). |
| `avatar-updates-out`        | `faf-lobby`              | Publishes persistent avatar selection events with routing key `success.player_avatar.update`.                              |

### Message Routing & Targeting

Outbound messages wrapped in `OutboundLobbyMessage<T>` support three targeting mechanisms:

- **`OutboundTarget.Session(sessionId)`**: Sent to a specific client connection session (e.g. login `welcome`, personal
  `social` info, or requested `avatar` list).
- **`OutboundTarget.Player(playerId)`**: Sent to all sessions belonging to a specific player ID (e.g. administrative
  `kick` or `kill` notices).
- **`OutboundTarget.All`**: Broadcast to all connected clients (e.g. batched `player_info` updates or server-wide `notice` broadcasts).

### Schema Registration

On startup, `LobbySchemaRegistrar` reads the `@JsonTypeInfo` / `@JsonSubTypes` annotations of the message hierarchies
and generates a draft 2020-12 JSON schema per command using the Victools Schema Generator (with its Jackson module):

| Base type                  | Direction | Route        |
|:---------------------------|:----------|:-------------|
| `SocialMessage.Client`     | inbound   | `social`     |
| `AdminMessage.Client`      | inbound   | `admin`      |
| `ConnectionMessage.Server` | outbound  | `connection` |
| `SocialMessage.Server`     | outbound  | `social`     |
| `AdminMessage.Server`      | outbound  | `admin`      |

The schemas are published to the schema registry exchange (`lobby-schema-register`) on startup, and resent directly to
the `reply-to` queue whenever a request arrives on `lobby-schema-request`.

---

## Supported Features

### Social & Relationship Management

- **Friends & Foes**: Add or remove relationships (`social_add` / `social_remove` with a `friend` and/or `foe` player
  ID) persisted in the database.
- **Autojoin Channels**: The `social` welcome message lists the chat channels to join on login: the default channel
  (`DEFAULT_CHANNEL_NAME`, `#aeolus` by default), `#<clan tag>_clan` if the player is in a clan, and `#moderators` if
  the player is in the `faf_moderators_global` user group. It also contains the player's friend and foe IDs.

### Avatar Selection

Handled through the `avatar` command:

- `action: list_avatar` lists the avatars assigned to the player (`ListAvatarsRequest`); the result is sent to the
  requesting session as an `avatar` message.
- `action: select` with an `avatar` URL selects that avatar (`SelectAvatarRequest`); without an `avatar` it clears the
  selection (`RemoveAvatarRequest`).

Selections are persisted and then published on `avatar-updates-out`. Every player service instance (including the
sender) consumes the event on `avatar-updates`, updates the online player, and marks them dirty, so the change reaches
clients with the next `player_info` batch.

### Administration & Moderation

All admin requests use the `admin` command. The requesting player's group permissions are checked; unauthorized requests
are logged and ignored.

- **Broadcast Notices** (`action: broadcast`, `message`): Sends a `notice` with style `info` to all clients
  (`BroadcastRequest`, permission `ADMIN_BROADCAST_MESSAGE`). Blank messages are ignored.
- **Close Lobby / Kick** (`action: closelobby`, `user_id`): Sends a `notice` with style `kick` to all sessions of the
  target player (`KickPlayerRequest`, permission `ADMIN_KICK_SERVER`).
- **Close Game** (`action: closeFA`, `user_id`): Sends a `notice` with style `kill` to all sessions of the target
  player, closing their running game (`ClosePlayerGameRequest`, permission `ADMIN_KICK_SERVER`).

---

## Technology Stack

- **Runtime / Framework**: [Quarkus](https://quarkus.io/) 4 (currently `4.0.0.Beta1`) with Java 25 & Virtual Threads
  (`@RunOnVirtualThread`)
- **Messaging**: RabbitMQ via SmallRye Reactive Messaging (`quarkus-messaging-rabbitmq`)
- **Persistence**: MariaDB via Hibernate ORM with Jakarta Data repositories (`quarkus-hibernate-orm`,
  `quarkus-data-processor`)
- **Scheduling**: `quarkus-scheduler`
- **Serialization & Schemas**: Jackson (`quarkus-jackson`) & Victools JSON Schema Generator
- **Geolocation**: MaxMind GeoIP2
- **Observability**: Micrometer with Prometheus registry (`quarkus-micrometer-registry-prometheus`), SmallRye Health
  checks, and JSON logging (`quarkus-logging-json`)
- **Code Quality**: Lombok, Error Prone with NullAway (JSpecify mode)

---

## Configuration

Configuration is managed via `application.yml` and can be overridden using environment variables.

### Environment Variables

| Variable               | Default Value                  | Description                                                          |
|:-----------------------|:-------------------------------|:---------------------------------------------------------------------|
| `DATABASE_HOST`        | `localhost`                    | MariaDB host address                                                 |
| `DATABASE_PORT`        | `3306`                         | MariaDB port                                                         |
| `DATABASE_NAME`        | `faf_lobby`                    | MariaDB database name                                                |
| `DATABASE_USERNAME`    | `faf-player-service`           | Database username                                                    |
| `DATABASE_PASSWORD`    | `banana`                       | Database password                                                    |
| `RABBITMQ_HOST`        | `localhost`                    | RabbitMQ broker host                                                 |
| `RABBITMQ_PORT`        | `5672`                         | RabbitMQ broker port                                                 |
| `RABBITMQ_USER`        | `faf-player-service`           | RabbitMQ username                                                    |
| `RABBITMQ_PASSWORD`    | `banana`                       | RabbitMQ password                                                    |
| `RABBITMQ_VHOST`       | `/faf-core`                    | RabbitMQ virtual host                                                |
| `HOSTNAME`             | `local`                        | Unique instance identifier (`faf.instance-id`) used for queue naming |
| `GEO_IP_DATABASE_PATH` | `/geoip/GeoLite2-Country.mmdb` | Path to the GeoLite2 Country database                                |
| `DEFAULT_CHANNEL_NAME` | `#aeolus`                      | Chat channel every player autojoins                                  |

### Database Version Check

On startup the service reads the version of the latest successful migration from the Flyway schema history
(`flyway_schema_history`) and refuses to start if it is below `faf.minimum-database-version` (currently `143`).

---

## Building & Running

### Prerequisites

- **Java 25+** (e.g., Eclipse Temurin JDK 25)
- **MariaDB** with the FAF database schema and **RabbitMQ**. Quarkus Dev Services are disabled, so both must be provided
  (e.g. via Docker).

### Development Mode

Run Quarkus live-coding development mode with:

```bash
./gradlew quarkusDev
```

### Running Tests

Execute test suites (the scheduler is disabled in the test profile):

```bash
./gradlew test
```

### Packaging & Docker

Build a runnable jar:

```bash
./gradlew build
```

The project includes `quarkus-container-image-docker`, so a Docker image can be built as part of the Gradle build:

```bash
./gradlew build -Dquarkus.container-image.build=true
```

Dockerfiles are also provided under `src/main/docker`:
- `Dockerfile.jvm` - Standard JVM deployment image
- `Dockerfile.legacy-jar` - JVM image using the legacy jar packaging
- `Dockerfile.native` - GraalVM native binary image
- `Dockerfile.native-micro` - Native binary on a minimal base image
