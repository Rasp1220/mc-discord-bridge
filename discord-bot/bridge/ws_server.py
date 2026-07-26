"""WebSocket server the Minecraft plugin connects to.

Only one Minecraft server is expected to be connected at a time. If the
plugin reconnects, the new connection replaces whatever was active before.
All handlers here are defensive: a malformed message or a send failure is
logged and swallowed rather than raised, so a flaky Minecraft-side
connection can never take the bot down.
"""

from __future__ import annotations

import asyncio
import json
import logging
from typing import Awaitable, Callable, Optional

from aiohttp import web

from . import protocol
from .config import Config

logger = logging.getLogger("bridge.ws_server")

ChatCallback = Callable[[str, str], Awaitable[None]]
StatusCallback = Callable[[str], Awaitable[None]]


class BridgeServer:
    def __init__(
        self,
        config: Config,
        on_chat: ChatCallback,
        on_server_start: StatusCallback,
        on_server_stop: StatusCallback,
    ) -> None:
        self._config = config
        self._on_chat = on_chat
        self._on_server_start = on_server_start
        self._on_server_stop = on_server_stop
        self._app = web.Application()
        self._app.router.add_get("/", self._handle_ws)
        self._runner: Optional[web.AppRunner] = None
        self._active_ws: Optional[web.WebSocketResponse] = None

    async def start(self) -> None:
        self._runner = web.AppRunner(self._app)
        await self._runner.setup()
        site = web.TCPSite(self._runner, self._config.bridge.host, self._config.bridge.port)
        await site.start()
        logger.info(
            "Bridge WebSocket server listening on %s:%s", self._config.bridge.host, self._config.bridge.port
        )

    async def stop(self) -> None:
        if self._active_ws is not None and not self._active_ws.closed:
            await self._active_ws.close()
        if self._runner is not None:
            await self._runner.cleanup()

    async def broadcast_to_minecraft(self, user: str, message: str) -> None:
        """Relays a Discord message to the connected Minecraft server.

        Best-effort: if no plugin is currently connected, the message is
        simply dropped rather than raising.
        """
        ws = self._active_ws
        if ws is None or ws.closed:
            logger.debug("No Minecraft server connected, dropping Discord message.")
            return
        payload = json.dumps({"type": protocol.TYPE_CHAT, "user": user, "message": message})
        try:
            await ws.send_str(payload)
        except Exception:
            logger.exception("Failed to send message to the Minecraft plugin.")

    async def _handle_ws(self, request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse(heartbeat=30)
        await ws.prepare(request)

        if not await self._authenticate(ws):
            await ws.close(code=4001, message=b"unauthorized")
            return ws

        if self._active_ws is not None and not self._active_ws.closed:
            logger.warning("A new Minecraft server connected while another connection was active; replacing it.")
            await self._active_ws.close()
        self._active_ws = ws
        logger.info("Minecraft plugin connected and authenticated.")

        try:
            async for msg in ws:
                if msg.type == web.WSMsgType.TEXT:
                    await self._handle_message(msg.data)
                elif msg.type == web.WSMsgType.ERROR:
                    logger.warning("Bridge WebSocket connection error: %s", ws.exception())
        finally:
            if self._active_ws is ws:
                self._active_ws = None
            logger.info("Minecraft plugin disconnected.")

        return ws

    async def _authenticate(self, ws: web.WebSocketResponse) -> bool:
        try:
            msg = await asyncio.wait_for(ws.receive(), timeout=self._config.bridge.auth_timeout)
        except asyncio.TimeoutError:
            logger.warning("Bridge connection timed out waiting for auth.")
            return False

        if msg.type != web.WSMsgType.TEXT:
            return False

        try:
            data = json.loads(msg.data)
        except json.JSONDecodeError:
            return False

        if data.get("type") != protocol.TYPE_AUTH or data.get("token") != self._config.bridge.secret:
            await ws.send_str(json.dumps({"type": protocol.TYPE_AUTH_FAIL}))
            logger.warning("Rejected bridge connection with an invalid or missing auth token.")
            return False

        await ws.send_str(json.dumps({"type": protocol.TYPE_AUTH_OK}))
        return True

    async def _handle_message(self, raw: str) -> None:
        try:
            data = json.loads(raw)
        except json.JSONDecodeError:
            logger.warning("Received malformed bridge message: %r", raw)
            return

        msg_type = data.get("type")
        if msg_type == protocol.TYPE_CHAT:
            await self._on_chat(str(data.get("player", "?")), str(data.get("message", "")))
        elif msg_type == protocol.TYPE_SERVER_START:
            await self._on_server_start(str(data.get("server_name", "Minecraft Server")))
        elif msg_type == protocol.TYPE_SERVER_STOP:
            await self._on_server_stop(str(data.get("server_name", "Minecraft Server")))
        else:
            logger.debug("Ignoring unknown bridge message type: %s", msg_type)
