# Bridge Communication Protocol

Version: 1.0

This document specifies the WebSocket protocol used between the Minecraft
plugin (`minecraft-plugin/`) and the Discord bot (`discord-bot/`).

## Roles

- **Server**: the Discord bot (`discord-bot`). It hosts a WebSocket endpoint
  (`ws://<bridge.host>:<bridge.port>/`) using aiohttp.
- **Client**: the Minecraft plugin (`minecraft-plugin`). It connects to the
  bot on plugin enable and reconnects with backoff whenever the connection
  drops.

Only one Minecraft server is expected to be connected at a time. If a new
connection authenticates while another is active, the bot closes the old one.

## Transport

- Plain WebSocket (`ws://`). If the bot and plugin communicate across an
  untrusted network, run the bot behind a TLS-terminating reverse proxy and
  use `wss://` instead — the protocol itself is transport-agnostic.
- All frames are text frames containing a single flat JSON object with a
  `type` field.

## Authentication

Both sides are configured with the same `bridge.secret` shared token.

1. Immediately after the WebSocket handshake completes, the client must send:

   ```json
   {"type": "auth", "token": "<shared-secret>"}
   ```

2. If the token matches, the server replies:

   ```json
   {"type": "auth_ok"}
   ```

3. If the token is missing, wrong, or no message arrives within
   `bridge.auth_timeout` seconds (default 10s), the server replies:

   ```json
   {"type": "auth_fail"}
   ```

   and closes the connection with close code `4001`.

4. Any message received before authentication succeeds (other than `auth`)
   is ignored.

## Message Types

### Client → Server (Minecraft → Discord)

| type           | fields                          | meaning                                   |
|----------------|----------------------------------|--------------------------------------------|
| `auth`         | `token`                          | Authenticate the connection.               |
| `chat`         | `player`, `message`              | A player sent a chat message in-game.      |
| `server_start` | `server_name`                    | The server finished starting up.           |
| `server_stop`  | `server_name`                    | The server is shutting down.               |
| `player_join`  | `player`                         | A player joined the server.                |
| `player_leave` | `player`                         | A player left the server.                  |

Example:

```json
{"type": "chat", "player": "Steve", "message": "hello world"}
```

### Server → Client (Discord → Minecraft)

| type        | fields            | meaning                                    |
|-------------|-------------------|---------------------------------------------|
| `auth_ok`   | –                 | Authentication succeeded.                    |
| `auth_fail` | –                 | Authentication failed; connection is closed. |
| `chat`      | `user`, `message` | A message was posted in the bridged Discord channel. |

Example:

```json
{"type": "chat", "user": "SomeDiscordUser", "message": "hi from discord"}
```

## Formatting

Message *content* is transmitted as plain, unformatted text. Each side is
responsible for applying its own display formatting from its local config:

- Minecraft plugin: `format.discord-to-minecraft` (`config.yml`), e.g.
  `"&9[Discord] &b{user}&f: {message}"`. Color codes are resolved against the
  template *before* `{user}`/`{message}` are substituted, so a Discord
  message can never inject an in-game color code.
- Discord bot: `format.minecraft_to_discord` (`config.yml`), e.g.
  `"**{player}**: {message}"`. Player names and message text are passed
  through `discord.utils.escape_mentions` / `escape_markdown`, and messages
  are sent with `allowed_mentions=AllowedMentions.none()`, so an in-game
  message can never ping `@everyone`, a role, or a user.

`player_join`/`player_leave` notices are formatted the same way as chat, via
`format.player_join`/`format.player_leave` (`config.yml`, Discord bot side
only), e.g. `"\U0001F7E2 **{player}** が参加しました"`. Player names go
through the same escaping and are sent to `discord.notify_channel_id` with
`allowed_mentions=AllowedMentions.none()`.

## Loop Prevention

- The Discord bot ignores its own messages and any message authored by a
  bot account.
- The Discord bot ignores messages in the chat channel that start with
  `discord.command_prefix` (default `!`) — those are treated as bot commands,
  not chat, and are never relayed to Minecraft.
- The Minecraft plugin never re-broadcasts a message it received from
  Discord back to Discord (chat received over the bridge is only broadcast
  in-game).

## Fault Tolerance

- **Bot offline / unreachable**: the plugin's WebSocket I/O runs off the
  server main thread. Sends are best-effort and non-blocking; if the socket
  isn't open and authenticated, the message is simply dropped. The plugin
  retries connecting on an exponential backoff (`bridge.reconnect.initial-delay`
  up to `bridge.reconnect.max-delay`, in seconds).
- **Plugin offline / unreachable**: the bot drops outgoing chat/notify
  messages if no plugin is currently connected, without raising.
- **Shutdown**: on `onDisable`, the plugin sends `server_stop` and closes the
  socket on a background thread bounded by `bridge.shutdown-flush-timeout-ms`
  (default 3000ms), so an unreachable bot cannot delay server shutdown.
