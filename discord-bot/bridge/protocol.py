"""Message type constants for the bridge WebSocket protocol.

See PROTOCOL.md at the repository root for the full specification.
"""

TYPE_AUTH = "auth"
TYPE_AUTH_OK = "auth_ok"
TYPE_AUTH_FAIL = "auth_fail"
TYPE_CHAT = "chat"
TYPE_SERVER_START = "server_start"
TYPE_SERVER_STOP = "server_stop"
