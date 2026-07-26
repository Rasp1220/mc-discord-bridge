"""discord.py client that relays chat and server status to Discord."""

from __future__ import annotations

import logging
from typing import Optional

import discord
from discord.ext import commands

from .config import Config

logger = logging.getLogger("bridge.discord_bot")


class DiscordBridgeBot(commands.Bot):
    def __init__(self, config: Config) -> None:
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(command_prefix=config.discord.command_prefix, intents=intents)
        self._config = config
        # Wired up by main.py once the BridgeServer exists.
        self.bridge_server = None

    async def on_ready(self) -> None:
        logger.info("Logged in to Discord as %s", self.user)

    async def on_message(self, message: discord.Message) -> None:
        if message.author == self.user or message.author.bot:
            return
        if message.channel.id != self._config.discord.chat_channel_id:
            return
        if message.content.startswith(self._config.discord.command_prefix):
            await self.process_commands(message)
            return

        content = message.clean_content.strip()
        if not content:
            if message.attachments:
                content = "[attachment]"
            else:
                return

        if self.bridge_server is not None:
            await self.bridge_server.broadcast_to_minecraft(message.author.display_name, content)

    async def relay_minecraft_chat(self, player: str, message_text: str) -> None:
        channel = self._get_text_channel(self._config.discord.chat_channel_id)
        if channel is None:
            return
        safe_player = discord.utils.escape_markdown(discord.utils.escape_mentions(player))
        safe_message = discord.utils.escape_markdown(discord.utils.escape_mentions(message_text))
        text = self._config.format.minecraft_to_discord.format(player=safe_player, message=safe_message)
        await channel.send(text, allowed_mentions=discord.AllowedMentions.none())

    async def notify_server_start(self, server_name: str) -> None:
        await self._send_status_embed(server_name, started=True)

    async def notify_server_stop(self, server_name: str) -> None:
        await self._send_status_embed(server_name, started=False)

    async def _send_status_embed(self, server_name: str, started: bool) -> None:
        channel = self._get_text_channel(self._config.discord.notify_channel_id)
        if channel is None:
            return
        embed = discord.Embed(
            title=f"\N{LARGE GREEN CIRCLE} {server_name} が起動しました" if started
            else f"\N{LARGE RED CIRCLE} {server_name} が停止します",
            color=discord.Color.green() if started else discord.Color.red(),
        )
        await channel.send(embed=embed)

    def _get_text_channel(self, channel_id: int) -> Optional[discord.abc.Messageable]:
        channel = self.get_channel(channel_id)
        if channel is None:
            logger.warning("Configured Discord channel %s was not found.", channel_id)
        return channel
