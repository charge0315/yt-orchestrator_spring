# Frontend API gap analysis (frontend client.ts 기준)

このドキュメントは、フロント（`yt-orchestrator_spring/src/main/frontend/src/api/client.ts`）が期待している API と、
- Node backend（`yt-orchestrator/packages/backend`）
- Spring WebFlux backend（`yt-orchestrator_spring`）
の実装状況を突き合わせた差分表です。

## 重要な結論（優先度順）

1. **認証APIの契約が一致していない**
   - フロントは `POST /api/auth/login` / `POST /api/auth/register` / `POST /api/auth/google`（credential送信）を呼ぶ想定。
   - Node/Spring はどちらも **OAuthリダイレクト開始 `GET /api/auth/google`** と **コールバック `GET /api/auth/google/callback`** 形式。
   - このままだとログインフローが成立しません（フロント or バックのどちらかを合わせる必要があります）。

2. **`/api/playlists`（プレイリストCRUD）の契約が不一致**
   - フロントは CRUD（`POST/PUT/DELETE`、`/songs` 追加削除、`GET /:id`）を期待。
   - Spring は `GET /api/playlists`（YouTube Data API list）+ `GET /:id/export` + `POST /import` のみ。
   - Node は `GET /api/playlists`（Mongoキャッシュ）しかありません。

3. **レスポンス形状が違う箇所が複数ある**（動くがフロントが壊れる可能性）
   - 例: Spring `GET /api/songs/search` は `{ results: [...] }` を返すが、フロントは `Song[]` を期待。

---

## Endpoint matrix

凡例: ✅=実装済, ⚠️=一部/挙動差, ❌=未実装

### Auth

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `POST /api/auth/register` | ❌ | ❌ | フロントのみ定義。Node/Springにユーザー登録APIなし。 |
| `POST /api/auth/login` | ❌ | ❌ | 同上。 |
| `POST /api/auth/google` (credential) | ❌ | ❌ | Node/Springは OAuth リダイレクト開始 `GET /api/auth/google`。 |
| `POST /api/auth/logout` | ✅ (`routes/auth.ts`) | ✅ (`AuthController`) | どちらも存在。レスポンス形状は要確認。 |
| `GET /api/auth/me` | ✅ (`routes/auth.ts`) | ✅ (`AuthController`) | Springは `reauthRequired` など付与。 |

### Cache

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| (フロント未定義) `POST /api/cache/refresh` | ✅ (`routes/cache.ts`) | ✅ (`CacheController`) | Nodeは `updateUserCaches()` 系の再構築中心。Springは `CacheRefreshService.refreshUserCache()`（意味が違う可能性大）。 |

### Songs

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/songs/search?query=` | ✅ (`routes/songs.ts`) | ⚠️ (`SongsController`) | Springは `{ results: Song[] }` 形式。フロントは `apiClient.get('/songs/search')` で **配列直**を期待しており、要調整。 |

### Recommendations

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/recommendations` | ✅ (`routes/recommendations.ts`) | ⚠️ (`RecommendationsController`) | Nodeは OpenAI 利用（`OPENAI_API_KEY`）+ fallback。Springは fallback相当のみ（OpenAI未実装）。 |

### Channels (cached)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/channels` | ✅ (`routes/cachedChannels.ts`) | ✅ (`ChannelsController`) | フロントは `any[]` なので形の厳密差は小さめ。 |
| `POST /api/channels` | ❌ | ✅ (`ChannelsController`) | Nodeは「購読チャンネルをDBキャッシュで返す」設計で、追加は別ジョブ/キャッシュ更新依存。Springは subscribe APIを提供。 |
| `DELETE /api/channels/:id` | ❌ | ✅ (`ChannelsController`) | Nodeは削除APIなし。 |

### Artists (cached / isArtist flag)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/artists` | ✅ (`routes/cachedArtists.ts`) | ✅ (`ArtistsController`) | Nodeは空のとき `updateUserCaches()` を試行し得る（daily gateあり）。Springは単純取得。 |
| `POST /api/artists` | ✅ | ✅ | isArtistフラグ更新に相当。 |
| `DELETE /api/artists/:id` | ✅ | ✅ | Nodeは subscriptionId / channelId 両方許容。SpringはID前提（実装要確認）。 |
| `GET /api/artists/new-releases` | ✅ | ✅ | 形は概ね似ている。 |

### Playlists (cached / export/import)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/playlists` | ✅ (`routes/allPlaylists.ts`) | ⚠️ (`PlaylistsController`) | NodeはMongoキャッシュを返す。SpringはYouTube Data API listを返す（レスポンス形も `Map{items,nextPageToken}`）。 |
| `GET /api/playlists/:id` | ❌ | ❌ | フロントは期待しているが、Node/Springには無し（Springは `/api/ytmusic/playlists/:id` が別用途で存在）。 |
| `POST /api/playlists` | ❌ | ❌ | 未実装。 |
| `PUT /api/playlists/:id` | ❌ | ❌ | 未実装。 |
| `DELETE /api/playlists/:id` | ❌ | ❌ | 未実装。 |
| `POST /api/playlists/:id/songs` | ❌ | ❌ | 未実装。 |
| `DELETE /api/playlists/:id/songs/:videoId` | ❌ | ❌ | 未実装。 |
| `GET /api/playlists/:id/export` | ❌ | ✅ (`PlaylistsController`) | Springのみ。 |
| `POST /api/playlists/import` | ❌ | ✅ (`PlaylistsController`) | Springのみ。 |

### YouTube Data proxy (`/api/youtube/*`)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/youtube/playlists` | ✅ (`routes/youtube.ts`, cache-only) | ✅ (`YoutubeController`, Data API) | NodeはMongoキャッシュのみ、SpringはYouTube Data API直参照。挙動が大きく違う。 |
| `GET /api/youtube/playlists/:id/items` | ❌ | ✅ (`YoutubeController`) | Node未実装。 |
| `POST /api/youtube/playlists` | ❌ | ✅ (`YoutubeController`) | Node未実装。 |
| `PUT /api/youtube/playlists/:id` | ❌ | ✅ (`YoutubeController`) | Node未実装。 |
| `DELETE /api/youtube/playlists/:id` | ❌ | ✅ (`YoutubeController`) | Node未実装。 |
| `POST /api/youtube/playlists/:id/videos` | ❌ | ✅ (`YoutubeController`) | Node未実装。 |
| `DELETE /api/youtube/playlists/:id/videos/:videoId` | ❌ | ✅ (`YoutubeController`) | Node未実装。 |
| `GET /api/youtube/search` | ✅ (`routes/youtube.ts`) | ✅ (`YoutubeController`) | 返却データ型が違う可能性はあるが、最低限は揃っている。 |

### YouTube Channels (`/api/youtube/channels/*`)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/youtube/channels` | ❌ | ✅ (`YoutubeChannelsController`) | Node未実装。 |
| `POST /api/youtube/channels` | ❌ | ✅ (`YoutubeChannelsController`) | Node未実装。 |
| `DELETE /api/youtube/channels/:id` | ❌ | ✅ (`YoutubeChannelsController`) | Node未実装。 |
| `GET /api/youtube/channels/latest-videos` | ❌ | ✅ (`YoutubeChannelsController`) | Node未実装。 |
| `POST /api/youtube/channels/:id/update-videos` | ❌ | ✅ (`YoutubeChannelsController`) | Node未実装。 |

### YouTube Recommendations (`/api/youtube/recommendations/*`)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/youtube/recommendations/channels` | ❌ | ✅ (`YoutubeRecommendationsController`) | Springは `/api/recommendations` の結果を元にチャンネル検索して整形。Node未実装。 |
| `GET /api/youtube/recommendations/videos` | ❌ | ✅ (`YoutubeRecommendationsController`) | Node未実装。 |

### YouTube OAuth helper (`/api/youtube/auth/*`)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/youtube/auth/url` | ❌ | ❌ | フロントのみ定義。現状は `/api/auth/google` に合わせる必要あり。 |
| `POST /api/youtube/auth/callback` | ❌ | ❌ | 同上。 |
| `GET /api/youtube/auth/status` | ❌ | ❌ | 同上。 |

### YT Music (`/api/ytmusic/*`)

| Frontend call | Node backend | Spring backend | Notes |
|---|---|---|---|
| `GET /api/ytmusic/auth/status` | ✅ | ✅ | 両方「常にconnected」。 |
| `GET /api/ytmusic/playlists?refresh=1` | ✅ (cache優先+daily gate+更新試行) | ⚠️ (Data API直参照) | Springは `refresh/force` を受け取るが無視。Nodeは429 `daily_limit` があり得る。 |
| `GET /api/ytmusic/playlists/:id` | ✅ | ✅ | 返却の構造は近いが、`songs[].artist` の取り方等は差が出る可能性あり。 |
| `GET /api/ytmusic/search?query=` | ✅ | ✅ | 概ね同等。 |

---

## 次に決めるべきこと（ブレを止める）

- **フロントを Spring に合わせるのか**（推奨: OAuthは `/api/auth/google` リダイレクト方式へ）
- **`/api/playlists` を CRUD にするのか**、それとも **Node/Spring どちらも `{items,nextPageToken}` の YouTube 風**に統一するのか
- **キャッシュ優先（Node流）をSpringに実装するのか**、それとも **常にYouTube Data API直参照**で行くのか
