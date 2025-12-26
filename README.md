# yt-orchestrator_spring

Spring Boot (WebFlux) + Reactive MongoDB で実装した YouTube Orchestrator のバックエンドです。

このリポジトリには、`yt-orchestrator` の React/Vite フロントエンドを同梱しており、Spring WebFlux から SPA として配信します。
（フロントは `src/main/frontend/` に配置されています）

## 主な機能

- Google OAuth ログイン（Cookie/WebSession）
- YouTube OAuth（YouTube Data API v3 へのアクセストークンをセッションに保持）
- YouTube 再生リスト操作（一覧/アイテム取得/追加/削除/検索）
- アーティスト/チャンネルの登録（MongoDBにキャッシュ）と最新動画の取得
- AIおすすめ（`OPENAI_API_KEY` があれば OpenAI、なければフォールバック）

## 必要環境

- Java 21
- MongoDB（クラウド推奨）
- Node.js（フロント同梱ビルド用。`./gradlew` 実行時に `src/main/frontend` の `npm install/build` を実行します）

## 環境変数

最低限:

- `MONGODB_URI` : MongoDB接続文字列（例: AtlasのURI）

必要に応じて:

- `FRONTEND_URL` : OAuthコールバック後の遷移先（例: `http://localhost:5173`）
- `OPENAI_API_KEY` : AIおすすめを有効化
- `OPENAI_MODEL` : OpenAIモデル名（未指定時は `gpt-4o-mini`）

Google OAuth / YouTube OAuth で必要な値は `src/main/resources/application.yml` を参照してください。
（プロジェクトの運用方法に合わせて環境変数/Secretsへ寄せるのがおすすめです）

## 起動方法

```bash
# Windows PowerShell
./gradlew bootRun
```

起動後:

- UI: <http://localhost:8080/>
- API: <http://localhost:8080/api>


またはテスト:

```bash
./gradlew test
```

## フロント開発（任意）

フロント単体で Vite dev server を起動し、`/api` を Spring(8080) にプロキシします。

```bash
cd src/main/frontend
npm install
npm run dev
```

## API概要

ベースパスは `/api` です。

### 認証

- `GET /api/auth/google` : Google OAuth 開始（リダイレクト）
- `GET /api/auth/callback` : Google OAuth コールバック
- `POST /api/auth/logout` : ログアウト
- `GET /api/auth/me` : 現在ログイン中ユーザー

### YouTube OAuth（フロント互換）

- `GET /api/youtube/auth/url` : 認可URLを返す
- `POST /api/youtube/auth/callback` : code を受け取りトークン交換しセッションへ保存
- `GET /api/youtube/auth/status` : 接続状態

### YouTube Data APIプロキシ（フロント互換）

- `GET /api/youtube/playlists`
- `GET /api/youtube/playlists/{id}/items`
- `POST /api/youtube/playlists/{id}/videos`
- `DELETE /api/youtube/playlists/{id}/videos/{videoId}`
- `GET /api/youtube/search?query=...&maxResults=...`

`/api/youtube/search` は以下のフィールドを返します（フロントの表示用に拡張）:

- `videoId`, `title`, `channelTitle`, `channelId`, `thumbnail`, `duration`, `publishedAt`

### アーティスト/チャンネル

- `GET /api/artists` / `POST /api/artists` / `DELETE /api/artists/{id}`
- `GET /api/artists/new-releases` : 登録チャンネルの最新動画（ホーム表示用）
- `GET /api/channels` / `POST /api/channels` / `DELETE /api/channels/{id}`

### YouTube Music（最小互換）

- `GET /api/ytmusic/auth/status`
- `GET /api/ytmusic/playlists`
- `GET /api/ytmusic/playlists/{id}`
- `GET /api/ytmusic/search?query=...`

## 開発メモ

- Cookieセッション（`withCredentials: true`）での利用を前提にしています。
- MongoDBはローカルではなくクラウド利用を想定し、`MONGODB_URI` を優先します。
