# FAF Player Service

[![Build Status](https://github.com/FAForever/faf-player-service/workflows/CI/badge.svg)](https://github.com/FAForever/faf-player-service/actions)

The **FAF Player Service** is a reactive backend microservice within the [Forged Alliance Forever (FAF)](https://www.faforever.com/) ecosystem. It is primarily responsible for **tracking player state** (online status, active sessions, leaderboard ratings, avatars, and social relationships) and **communicating real-time state updates to clients** by publishing and forwarding messages over **RabbitMQ**.

---

## Table of Contents

- [Core Responsibilities](#core-responsibilities)
- [Architecture & State Tracking](#architecture--state-tracking)
  - [Player State Lifecycle](#player-state-lifecycle)
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
- [Building & Running](#building--running)
  - [Prerequisites](#prerequisites)
  - [Development Mode](#development-mode)
  - [Running Tests](#running-tests)
  - [Packaging & Docker](#packaging--docker)

---

## Core Responsibilities

1. **Player State Tracking**: Manages in-memory player state across multiple concurrent sessions per player, tracking online/offline status, clan affiliation, assigned avatars, and rating statistics across all leaderboards.
2. **Client State Communication**: Distributes player info, initial connection state, and incremental player state changes to connected game/lobby clients via RabbitMQ message exchanges.
3. **Social & Moderation Services**: Handles player friend/foe relationship updates, avatar selections, and administrative actions (broadcast notices, kick commands, and lobby/game terminations).
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
|   +-----------------------+                 |  - sessionPlayerMap            |   |
|                                             |  - sessionExpirationMap        |   |
|   +-----------------------+                 |  - dirtyPlayers set            |   |
|   |   Avatar / Ratings    | --------------> +----------------+---------------+   |
|   |       Updates         |                                  |                   |
|   +-----------------------+                                  | Batched (1s)      |
|                                                              v                   |
|   +-----------------------+     Dispatches   +-------------------------------+   |
|   |     PlayerService     | -------------->  | Outbound Lobby Message Queue  |   |
|   | (Social/Admin actions)|                  | (lobby-message-outbound)      |   |
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
- **`playerIdMap`**: Maps unique player IDs to active `Player` domain objects.
- **`sessionPlayerMap`**: Maps active connection session IDs to their corresponding `Player`.
- **`playerSessionMap`**: Tracks all active session IDs associated with a player (supporting multi-session / reconnect scenarios).
- **`sessionExpirationMap`**: Tracks TTLs for player sessions to automatically discard abandoned or stale connections.

When all sessions for a player disconnect or expire, the player is transitioned to `OFFLINE` and flagged as dirty so an offline update is broadcast to other clients before the player record is purged from memory.

### Player Country Detection

This application makes use of the [GeoLite2](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data) database to
identify players country by ip.
If this file is not present all players will have no country

### Session Synchronization

- **Startup Recovery**: On service startup, `PlayerStateTracker` sends a session inquiry over `player-session-request` (`lobby-session-request` exchange) to reconcile all currently active client sessions from the lobby gateway.
- **Session Registration**: Listens on `player-session-state` (`lobby-session-state` exchange) for:
  - `new`: A new client connection. Triggers welcome packages (`LoginSuccessResponse`, `SocialInfo`, and initial full `PlayerInfoList`) and marks the player as dirty for broadcast.
  - `existing`: Re-registers/refreshes an active session expiration window without resending redundant welcome payloads.
  - `gone`: Disassociates a session; marks the player offline if no other sessions remain active.
- **Session Expiration**: A scheduled task runs every minute (`removeExpiredSessions`) to clean up sessions that exceeded their time-to-live.

### Periodic Rating Synchronization

A scheduled task executes every minute (`updatePlayerLeaderboardRatings`) to query MariaDB for any rating changes (`mean`, `deviation`, `totalGames`) across all leaderboards that occurred since the last evaluation timestamp for online players. Players with updated ratings are marked dirty.

### Batch State Propagation (Dirty Player Updates)

To minimize network traffic and avoid flooding clients with high-frequency updates, player modifications (avatar changes, rating updates, login/logout events) mark the affected `Player` as dirty.

A scheduled task runs every second (`sendUpdateForDirtyPlayers`):
1. Takes an immutable snapshot of all dirty players.
2. Maps them to `PlayerInfo` transfer objects.
3. Publishes a batched `player_info` (`SocialMessage.PlayerInfoList`) broadcast message to `lobby-message-outbound`.

---

## RabbitMQ Messaging & Client Communication

The service uses RabbitMQ via SmallRye Reactive Messaging to ingest client commands and forward responses.

### Inbound Channels

| Channel | Exchange | Routing Key / Queue | Description |
| :--- | :--- | :--- | :--- |
| `player-session-state` | `lobby-session-state` | `player.session.state.${instance-id}` | Ingests session lifecycle events (`new`, `existing`, `gone`). |
| `social-inbound` | `lobby-message-inbound` | `lobby.social.#` | Client social requests (`social_add`, `social_remove`, avatar listing/selection). |
| `admin-inbound` | `lobby-message-inbound` | `lobby.admin.#` | Privileged admin requests (broadcast notices, kick, close game). |
| `avatar-updates` | `faf-lobby` | `success.player_avatar.update` | Real-time notifications of avatar updates. |
| `lobby-schema-request` | `lobby-schema-request` | `player.schema.request.${instance-id}` | Inbound requests for JSON message schemas. |

### Outbound Channels

| Channel | Exchange | Description |
| :--- | :--- | :--- |
| `lobby-outbound` | `lobby-message-outbound` | Outbound messages destined for clients (targeted by session, player ID, or broadcast). |
| `lobby-schema-registration` | `lobby-schema-register` | Publishes generated JSON schemas for client and server commands. |
| `player-session-request` | `lobby-session-request` | Requests active session dump from the lobby gateway during startup. |
| `avatar-updates-out` | `faf-lobby` | Emits avatar assignment update events across services. |

### Message Routing & Targeting

Outbound messages wrapped in `OutboundLobbyMessage<T>` support three targeting mechanisms:
- **`OutboundTarget.Session(sessionId)`**: Sent to a specific client connection session (e.g. login `welcome`, personal `social` list, or requested `avatar` list).
- **`OutboundTarget.Player(playerId)`**: Sent to all sessions belonging to a specific player ID (e.g. administrative kick or close game notices).
- **`OutboundTarget.All`**: Broadcast to all connected clients (e.g. batched `player_info` updates or server-wide `notice` broadcasts).

### Schema Registration

On startup, `LobbySchemaRegistrar` scans message definitions annotated with `@JsonTypeInfo` and `@JsonSubTypes` (`SocialMessage`, `AdminMessage`, `ConnectionMessage`), generates draft 2020-12 JSON schemas using Jackson and Victools Schema Generator, and publishes them to the schema registry exchange (`lobby-schema-register`).

---

## Supported Features

### Social & Relationship Management
- **Friends & Foes**: Add or remove relationships (`SocialAddRequest`, `SocialRemoveRequest`) persisted in the database.
- **Autojoin Channels**: Dispatches initial chat channels on login (e.g., `#clan_tag` if in a clan, `#moderators` if assigned moderation permissions).

### Avatar Selection
- Lists available avatars unlocked/assigned to the player (`ListAvatarsRequest`).
- Selects or removes the player's active avatar (`SelectAvatarRequest`, `RemoveAvatarRequest`), immediately propagating updates across the player service cluster and out to clients.

### Administration & Moderation
- **Broadcast Notices**: Sends server-wide announcements to all clients (`BroadcastRequest` with permission `ADMIN_BROADCAST_MESSAGE`).
- **Kick Player**: Disconnects a user from the lobby (`KickPlayerRequest` with permission `ADMIN_KICK_SERVER`).
- **Close Game**: Forces termination of a player's active game lobby (`ClosePlayerGameRequest` with permission `ADMIN_KICK_SERVER`).

---

## Technology Stack

- **Runtime / Framework**: [Quarkus](https://quarkus.io/) with Java 25 & Virtual Threads (`@RunOnVirtualThread`)
- **Messaging**: RabbitMQ via SmallRye Reactive Messaging (`quarkus-messaging-rabbitmq`)
- **Persistence**: MariaDB via Hibernate ORM with Panache (`quarkus-hibernate-orm-panache`)
- **Serialization & Validation**: Jackson (`quarkus-jackson`) & Victools JSON Schema Generator
- **Observability**: Micrometer with Prometheus registry (`quarkus-micrometer-registry-prometheus`) & SmallRye Health Checks

---

## Configuration

Configuration is managed via `application.yml` and can be overridden using environment variables.

### Environment Variables

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `DATABASE_HOST` | `localhost` | MariaDB host address |
| `DATABASE_PORT` | `3306` | MariaDB port |
| `DATABASE_NAME` | `faf_lobby` | MariaDB database name |
| `DATABASE_USERNAME` | `faf-player-service` | Database username |
| `DATABASE_PASSWORD` | `banana` | Database password |
| `RABBITMQ_HOST` | `localhost` | RabbitMQ broker host |
| `RABBITMQ_PORT` | `5672` | RabbitMQ broker port |
| `RABBITMQ_USER` | `faf-lobby-server` | RabbitMQ username |
| `RABBITMQ_PASSWORD` | `banana` | RabbitMQ password |
| `RABBITMQ_VHOST` | `/faf-core` | RabbitMQ virtual host |
| `HOSTNAME` | `local` | Unique instance identifier for queue naming |

---

## Building & Running

### Prerequisites

- **Java 25+** (e.g., Eclipse Temurin JDK 25)
- **Docker** (optional, for containerized execution or local MariaDB/RabbitMQ services)

### Development Mode

Run Quarkus live-coding development mode with:

```bash
./gradlew quarkusDev
```

### Running Tests

Execute test suites:

```bash
./gradlew test
```

### Packaging & Docker

Build a runnable jar:

```bash
./gradlew build
```

Container images can be built using the provided Dockerfiles under `src/main/docker`:
- `Dockerfile.jvm` - Standard JVM deployment image
- `Dockerfile.native` - GraalVM native binary image
