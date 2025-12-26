# YouTube Orchestrator - セットアップと動作確認ガイド

## 環境確認

このアプリケーションは以下の環境で動作確認済みです：

- **Java**: 21.0.1 (OpenJDK Temurin)
- **MongoDB**: 7.0.28
- **Spring Boot**: 3.3.6
- **Node.js**: 20.x (フロントエンドビルド用)

## 前提条件

### 1. Java 21のインストール

```bash
# SDKMANを使用してJava 21をインストール
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.1-tem

# インストール確認
java -version
```

### 2. MongoDBのインストール

```bash
# MongoDB GPGキーを追加
curl -fsSL https://www.mongodb.org/static/pgp/server-7.0.asc | sudo gpg --dearmor -o /usr/share/keyrings/mongodb-server-7.0.gpg

# MongoDBリポジトリを追加
echo "deb [ arch=amd64,arm64 signed-by=/usr/share/keyrings/mongodb-server-7.0.gpg ] https://repo.mongodb.org/apt/ubuntu jammy/mongodb-org/7.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-7.0.list

# パッケージリストを更新
sudo apt-get update

# MongoDBをインストール
sudo apt-get install -y mongodb-org

# データディレクトリを作成
sudo mkdir -p /data/db
sudo chown -R $(whoami) /data/db

# MongoDBを起動
mongod --fork --logpath /tmp/mongod.log --dbpath /data/db
```

## アプリケーションのビルドと起動

### 1. プロジェクトのビルド

```bash
cd /home/user/webapp
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew build -x test
```

ビルド時に以下が自動的に実行されます：
- フロントエンド（React/Vite）の `npm install`
- フロントエンドのビルド（`npm run build`）
- ビルドされたフロントエンドファイルが `src/main/resources/static` にコピー

### 2. アプリケーションの起動

#### オプション A: 起動スクリプトを使用（推奨）

```bash
cd /home/user/webapp
./start.sh
```

このスクリプトは以下を自動的に実行します：
- MongoDBの起動状態を確認し、必要に応じて起動
- 環境変数を設定してSpring Bootアプリケーションを起動

#### オプション B: 手動起動

```bash
cd /home/user/webapp
source "$HOME/.sdkman/bin/sdkman-init.sh"

# ローカルMongoDBを使用する場合
MONGODB_URI=mongodb://localhost:27017/yt \
OPENAI_API_KEY=your_openai_api_key \
./gradlew bootRun

# MongoDB Atlasを使用する場合（注意：ネットワーク制限がある環境では接続できない場合があります）
# MONGODB_URI="mongodb+srv://username:password@cluster.mongodb.net/yt?retryWrites=true&w=majority" \
# OPENAI_API_KEY=your_openai_api_key \
# ./gradlew bootRun
```

起動ログに以下のメッセージが表示されれば成功です：
```
Started YtOrchestratorSpringApplication in X.XXX seconds
Netty started on port 8080 (http)
Monitor thread successfully connected to server
```

## 動作確認

### 1. ヘルスチェック

```bash
curl http://localhost:8080/actuator/health
```

レスポンス例：
```json
{"status":"UP"}
```

### 2. フロントエンドの確認

ブラウザで以下のURLにアクセス：
```
http://localhost:8080/
```

または公開URL：
```
https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai
```

### 3. APIエンドポイントの確認

```bash
# 認証状態の確認（未ログイン時は401が返る）
curl http://localhost:8080/api/auth/me

# アーティスト一覧（認証が必要）
curl http://localhost:8080/api/artists
```

## トラブルシューティング

### MongoDB Atlasへの接続エラー

サンドボックス環境やファイアウォールがある環境では、MongoDB Atlasへの接続（SSL/TLS）が失敗する場合があります。

**エラー例**:
```
javax.net.ssl.SSLException: Received fatal alert: internal_error
MongoSocketWriteException: Exception sending message
```

**解決方法**:
1. ローカルMongoDBを使用する（推奨）
   ```bash
   MONGODB_URI=mongodb://localhost:27017/yt ./gradlew bootRun
   ```

2. MongoDB Atlasのネットワークアクセス設定を確認
   - MongoDB Atlas コンソールで「Network Access」を確認
   - 必要に応じてIPアドレスを許可リストに追加

3. データベース名が含まれているか確認
   - ❌ `mongodb+srv://user:pass@cluster.mongodb.net/`
   - ✅ `mongodb+srv://user:pass@cluster.mongodb.net/yt?retryWrites=true&w=majority`

### MongoDBに接続できない場合

1. MongoDBが起動しているか確認：
```bash
ps aux | grep mongod
```

2. MongoDBのログを確認：
```bash
tail -f /tmp/mongod.log
```

3. MongoDBを再起動：
```bash
# プロセスを停止
pkill mongod

# 再起動
mongod --fork --logpath /tmp/mongod.log --dbpath /data/db
```

### ポート8080が使用中の場合

```bash
# ポート8080を使用しているプロセスを確認
lsof -i :8080

# プロセスを終了
kill -9 <PID>
```

### Gradleビルドが失敗する場合

```bash
# Gradleキャッシュをクリア
./gradlew clean

# 再ビルド
./gradlew build -x test
```

## 環境変数

### 必須の環境変数

- `MONGODB_URI`: MongoDB接続文字列
  - ローカル: `mongodb://localhost:27017/yt`
  - Atlas: `mongodb+srv://username:password@cluster.mongodb.net/yt?retryWrites=true&w=majority`

### オプションの環境変数

- `OPENAI_API_KEY`: AIおすすめ機能を有効化
  - 設定済み: OpenAI APIを使用してAIレコメンデーションを提供
  - 未設定: フォールバック機能を使用
- `OPENAI_MODEL`: OpenAIモデル名（デフォルト: `gpt-4o-mini`）
- `FRONTEND_URL`: OAuthコールバック後の遷移先（開発時）

### 現在の設定

このセットアップでは以下の設定が使用されています：

```bash
# MongoDB（ローカル）
MONGODB_URI="mongodb://localhost:27017/yt"

# OpenAI API
OPENAI_API_KEY="<your-openai-api-key>"
```

> ⚠️ **セキュリティ注意**: 本番環境では環境変数やシークレット管理システムを使用してAPIキーを保護してください。

## 開発モード

フロントエンドのみを開発する場合：

```bash
cd src/main/frontend
npm install
npm run dev
```

Vite dev serverが起動し、`http://localhost:5173` でアクセス可能になります。
APIコールは `/api` を Spring Boot（8080）にプロキシします。

## 本番デプロイ

本番環境では以下を推奨：
- MongoDB Atlas（クラウドMongoDB）を使用
- `MONGODB_URI` 環境変数を設定
- Google OAuth認証情報を設定
- YouTube Data API認証情報を設定

詳細は `README.md` を参照してください。
