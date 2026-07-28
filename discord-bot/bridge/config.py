"""Loads config.yml and the bot token from the environment."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

import yaml
from dotenv import load_dotenv


@dataclass(frozen=True)
class DiscordSettings:
    chat_channel_id: int
    notify_channel_id: int
    command_prefix: str


@dataclass(frozen=True)
class BridgeSettings:
    host: str
    port: int
    secret: str
    auth_timeout: float


@dataclass(frozen=True)
class FormatSettings:
    minecraft_to_discord: str
    discord_to_minecraft: str
    player_join: str
    player_leave: str


@dataclass(frozen=True)
class Config:
    token: str
    discord: DiscordSettings
    bridge: BridgeSettings
    format: FormatSettings


def load_config(config_path: str | Path = "config.yml", env_path: str | Path = ".env") -> Config:
    load_dotenv(dotenv_path=env_path)

    token = os.environ.get("DISCORD_BOT_TOKEN")
    if not token:
        raise RuntimeError(
            "DISCORD_BOT_TOKEN is not set. Copy .env.example to .env and fill in your bot token."
        )

    path = Path(config_path)
    if not path.exists():
        raise RuntimeError(
            f"{path} not found. Copy config.example.yml to {path} and fill in your settings."
        )

    with path.open("r", encoding="utf-8") as f:
        raw = yaml.safe_load(f) or {}

    discord_raw = raw.get("discord", {})
    bridge_raw = raw.get("bridge", {})
    format_raw = raw.get("format", {})

    secret = str(bridge_raw.get("secret", ""))
    if not secret or secret == "CHANGE_ME_SHARED_SECRET":
        raise RuntimeError(
            "bridge.secret has not been set in config.yml - it must match the "
            "minecraft-plugin's config.yml exactly."
        )

    return Config(
        token=token,
        discord=DiscordSettings(
            chat_channel_id=int(discord_raw.get("chat_channel_id", 0)),
            notify_channel_id=int(discord_raw.get("notify_channel_id", 0)),
            command_prefix=str(discord_raw.get("command_prefix", "!")),
        ),
        bridge=BridgeSettings(
            host=str(bridge_raw.get("host", "0.0.0.0")),
            port=int(bridge_raw.get("port", 8765)),
            secret=secret,
            auth_timeout=float(bridge_raw.get("auth_timeout", 10)),
        ),
        format=FormatSettings(
            minecraft_to_discord=str(format_raw.get("minecraft_to_discord", "**{player}**: {message}")),
            discord_to_minecraft=str(format_raw.get("discord_to_minecraft", "[Discord] [{user}] {message}")),
            player_join=str(format_raw.get("player_join", "\N{LARGE GREEN CIRCLE} **{player}** が参加しました")),
            player_leave=str(format_raw.get("player_leave", "\N{LARGE RED CIRCLE} **{player}** が退出しました")),
        ),
    )
