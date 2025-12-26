# YouTube Orchestrator - 動作確認ガイド

## ✅ アプリケーション稼働中

YouTube Orchestratorが正常に起動し、アクセス可能な状態になっています。

## 🌐 アクセスURL

### メインURL（フロントエンド）
```
https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai
```

**ステータス**: ✅ **正常稼働中**

## 📊 動作確認結果

### 1. フロントエンド（React/Vite SPA）
- ✅ **ページタイトル**: YouTube Orchestrator
- ✅ **HTML読み込み**: 成功
- ✅ **レスポンスタイム**: 約37秒（初回ロード）
- ⚠️ **一部リソース**: OAuth未設定により403/404エラー（正常動作）

### 2. バックエンドAPI
- ✅ **ヘルスチェック**: `{"status":"UP"}`
- ✅ **サーバー**: Netty on port 8080
- ✅ **MongoDB**: 接続成功（yt-orchestrator）

## 🎯 主要エンドポイント

### フロントエンド
```
https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai/
```

### APIエンドポイント

#### 1. ヘルスチェック
```bash
curl https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai/api/health

# レスポンス
{"status":"ok","message":"YouTube Orchestrator API は稼働中です"}
```

#### 2. Actuator ヘルスチェック
```bash
curl https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai/actuator/health

# レスポンス
{"status":"UP"}
```

#### 3. 認証ステータス
```bash
curl https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai/api/auth/me

# レスポンス（未ログイン時）
{"timestamp":"...","path":"/api/auth/me","status":401,"error":"Unauthorized"}
```

#### 4. YouTube認証ステータス
```bash
curl https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai/api/youtube/auth/status

# レスポンス
{"connected":false}
```

## 🖥️ ブラウザでの確認方法

### 手順1: URLを開く
1. ブラウザを開く
2. 以下のURLをアドレスバーに貼り付け
   ```
   https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai
   ```
3. Enterキーを押す

### 手順2: 画面の確認
- **ページタイトル**: "YouTube Orchestrator"が表示される
- **画面**: React アプリケーションのUIが表示される
- **コンソールエラー**: OAuth未設定による403/404エラーが出るが、これは正常（OAuth設定後に解消）

## 📋 現在の状態

### ✅ 正常動作中
- フロントエンド（React/Vite SPA）
- バックエンドAPI（Spring Boot WebFlux）
- MongoDB接続（ローカル）
- ヘルスチェックAPI
- OpenAI統合

### ⚠️ OAuth設定待ち
以下の機能はOAuth設定完了後に利用可能：
- Google ログイン
- YouTube Data API連携
- プレイリスト管理
- アーティスト登録
- 動画検索

## 🔧 技術情報

### アプリケーション構成
- **フレームワーク**: Spring Boot 3.3.6 (WebFlux)
- **フロントエンド**: React + Vite
- **データベース**: MongoDB 7.0.28
- **Java**: 21.0.1 (OpenJDK Temurin)
- **ポート**: 8080

### データベース
- **接続文字列**: mongodb://localhost:27017/yt-orchestrator
- **データベース名**: yt-orchestrator
- **ステータス**: ✅ 接続中

### OpenAI統合
- **APIキー**: ✅ 設定済み
- **機能**: AIレコメンデーション有効

## 🐛 よくある問題と解決策

### Q1: 画面が真っ白
**原因**: ブラウザキャッシュまたはJavaScriptエラー  
**解決策**:
1. ブラウザのキャッシュをクリア（Ctrl+Shift+Delete）
2. ページを再読み込み（Ctrl+F5）
3. ブラウザのコンソールでエラーを確認

### Q2: 403/404エラーが表示される
**原因**: OAuth設定が不完全（正常動作）  
**解決策**: 
- これは期待される動作です
- OAuth設定完了後に解消されます
- 基本的な画面表示には影響しません

### Q3: "Unauthorized"エラー
**原因**: ログインしていない（正常動作）  
**解決策**:
- これは正常な動作です
- ログイン機能を使用するにはOAuth設定が必要です

### Q4: ページの読み込みが遅い
**原因**: 初回読み込みまたはネットワーク遅延  
**解決策**:
- 初回は30-40秒かかることがあります
- 2回目以降は高速になります
- ブラウザキャッシュが有効になります

## 📱 動作確認チェックリスト

### 基本動作
- [ ] URLにアクセスできる
- [ ] ページタイトルが"YouTube Orchestrator"と表示される
- [ ] HTMLが正しく読み込まれる
- [ ] Reactアプリケーションが起動する

### APIエンドポイント
- [ ] `/api/health` が正常に応答する
- [ ] `/actuator/health` が"UP"を返す
- [ ] `/api/auth/me` が401を返す（未ログイン時）

### ブラウザコンソール
- [ ] ページが表示される
- [ ] コンソールに致命的なエラーがない
- [ ] 403/404エラーは無視してよい（OAuth未設定）

## 🎉 動作確認完了

以下を確認できれば、アプリケーションは正常に動作しています：

1. ✅ URLにアクセスできる
2. ✅ ページタイトルが表示される
3. ✅ ヘルスチェックAPIが応答する
4. ✅ MongoDB接続が確立されている

## 📞 サポート

問題が解決しない場合は、以下を確認してください：
- アプリケーションログ（`/tmp/mongod.log`）
- ブラウザのコンソールログ
- ネットワーク接続

---

**アクセスURL**: https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai

**ステータス**: ✅ **正常稼働中**

**最終確認**: 2025年12月26日 05:16 UTC
