# mc-discord-bridge

Minecraft (Java Edition, Paper/Purpur v1.21.11) と Discord 間で、チャットの双方向同期
とサーバーの起動・停止通知を行う連携システムです。

- `minecraft-plugin/` — Paper/Purpur 用プラグイン（Java）
- `discord-bot/` — discord.py 製 Discord Bot（Python 3.10+）
- `PROTOCOL.md` — 両者を繋ぐ WebSocket 通信プロトコル仕様書

両者は WebSocket で接続され、共通の秘密トークンで認証します。詳細は
[PROTOCOL.md](PROTOCOL.md) を参照してください。

## 機能

- **マイクラ → Discord**: プレイヤーの発言を指定チャンネルへ転送（メンション・
  Markdown はエスケープ済み、`@everyone` 等への誤爆なし）
- **Discord → マイクラ**: 指定チャンネルの投稿をサーバー全体チャットへ転送
  （Bot自身の発言・コマンドプレフィックス付きメッセージは無視し、ループを防止）
- **起動/停止通知**: サーバー起動完了時・シャットダウン時に Discord へ Embed 通知
- **障害耐性**: Discord Bot がオフラインでもマイクラ側の処理はブロックされず、
  自動的に再接続を試みます

## セットアップ

### 1. Discord Bot (`discord-bot/`)

```bash
cd discord-bot
pip install -r requirements.txt
cp .env.example .env            # DISCORD_BOT_TOKEN を設定
cp config.example.yml config.yml  # チャンネルID・secret 等を設定
python main.py
```

`config.yml` で設定する項目:

- `discord.chat_channel_id` / `discord.notify_channel_id`: 対象チャンネルID
- `bridge.host` / `bridge.port`: プラグインが接続してくる待受アドレス
- `bridge.secret`: プラグイン側と一致させる共有シークレット

### 2. Minecraft プラグイン (`minecraft-plugin/`)

```bash
cd minecraft-plugin
mvn package
```

生成された `target/mc-discord-bridge-plugin-1.0.0.jar` をサーバーの `plugins/`
に配置して起動すると、既定の `config.yml` が生成されます。以下を Discord Bot
側と一致させてください:

- `bridge.host` / `bridge.port`: Discord Bot の待受アドレス
- `bridge.secret`: Discord Bot 側と同じ値

### 3. 動作確認

1. Discord Bot を先に起動する
2. Minecraft サーバーを起動する → 起動完了時に Discord へ通知が届くこと、
   プラグインログに認証成功のログが出ることを確認
3. ゲーム内チャット・Discord チャンネル双方でメッセージが転送されることを確認
4. Minecraft サーバーを停止する → Discord へ停止通知が届くことを確認

## ディレクトリ構成

```
mc-discord-bridge/
├── PROTOCOL.md                 # 通信プロトコル仕様書
├── minecraft-plugin/           # Java プラグイン (Maven)
│   ├── pom.xml
│   └── src/main/java/com/mcdiscordbridge/
│       ├── DiscordBridgePlugin.java
│       ├── bridge/             # WebSocket クライアント・再接続管理
│       ├── config/             # config.yml の型付きラッパー
│       ├── listener/           # チャットイベント購読
│       └── util/                # JSON / フォーマット補助
└── discord-bot/                # Python Bot
    ├── main.py
    └── bridge/
        ├── config.py            # config.yml / .env 読み込み
        ├── ws_server.py         # WebSocket サーバー (aiohttp)
        ├── discord_bot.py       # discord.py クライアント
        └── protocol.py          # メッセージ種別定数
```
