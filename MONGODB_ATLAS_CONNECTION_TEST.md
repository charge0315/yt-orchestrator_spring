# MongoDB Atlas 接続テスト レポート

## 🔍 接続テスト日時
2025年12月26日

## 📊 テスト結果

### ❌ MongoDB Atlas 接続: 失敗

**接続URI**: 
```
mongodb+srv://charge:cWOafZTq7zvlwhfd@my-mongo-cluster.nylyz9i.mongodb.net/yt-orchestrator
```

**エラー詳細**:
```
javax.net.ssl.SSLException: Received fatal alert: internal_error
com.mongodb.MongoSocketWriteException: Exception sending message
```

### 🔍 原因分析

#### 1. SSL/TLS ハンドシェイクエラー
MongoDB Atlasは必ずSSL/TLS暗号化接続を要求しますが、このサンドボックス環境からの接続時にSSLハンドシェイクが失敗しています。

#### 2. ネットワーク制限
サンドボックス環境からMongoDB Atlasクラスタ（ac-uuyuml6-shard-00-*.nylyz9i.mongodb.net:27017）への接続がタイムアウトまたはSSLレベルでブロックされています。

#### 3. 考えられる原因
- **ファイアウォール制限**: サンドボックス環境の送信トラフィック制限
- **SSL証明書の問題**: MongoDB AtlasのSSL証明書とJava 21のTLS設定の不一致
- **ネットワークポリシー**: MongoDB Atlas側のIP制限（0.0.0.0/0でも接続不可）
- **プロキシ設定**: サンドボックス環境の透過プロキシの影響

### ✅ 代替案: ローカルMongoDB - 成功

**接続URI**: 
```
mongodb://localhost:27017/yt-orchestrator
```

**結果**: ✅ 接続成功

**確認事項**:
- MongoDB 7.0.28 がローカルで起動中
- アプリケーションから正常に接続
- ヘルスチェック: `{"status":"UP"}`
- データベース名: `yt-orchestrator`

## 🔧 実施した接続テスト

### 1. Spring Boot アプリケーションからの接続
```bash
# MongoDB Atlas
MONGODB_URI="mongodb+srv://charge:***@my-mongo-cluster.nylyz9i.mongodb.net/yt-orchestrator?retryWrites=true&w=majority"
結果: SSL接続エラー

# ローカルMongoDB
MONGODB_URI="mongodb://localhost:27017/yt-orchestrator"
結果: ✅ 成功
```

### 2. mongosh CLIツールでの接続
```bash
mongosh "mongodb+srv://charge:***@my-mongo-cluster.nylyz9i.mongodb.net/yt-orchestrator"
結果: タイムアウト（30秒）
```

## 📋 エラーログ詳細

### SSL例外スタックトレース
```
javax.net.ssl.SSLException: Received fatal alert: internal_error
    at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:132)
    at java.base/sun.security.ssl.Alert.createSSLException(Alert.java:117)
    at java.base/sun.security.ssl.TransportContext.fatal(TransportContext.java:365)
    at java.base/sun.security.ssl.Alert$AlertConsumer.consume(Alert.java:287)
    at java.base/sun.security.ssl.TransportContext.dispatch(TransportContext.java:204)
    at java.base/sun.security.ssl.SSLTransport.decode(SSLTransport.java:172)
    ...
```

### MongoDB ドライバーエラー
```
com.mongodb.MongoSocketWriteException: Exception sending message
    at com.mongodb.internal.connection.InternalStreamConnection.translateWriteException
    at com.mongodb.internal.connection.InternalStreamConnection.sendMessage
    at com.mongodb.internal.connection.InternalStreamConnection.sendCommandMessage
    ...
```

## 💡 推奨事項

### 現在の対応
✅ **ローカルMongoDBを使用** (推奨)
- データベース名: `yt-orchestrator`
- 完全に動作可能
- 開発・テスト環境として十分

### 本番環境での対応案

#### オプション 1: 本番サーバーからの接続テスト
本番デプロイ環境（AWS、GCP、Azureなど）からMongoDB Atlasへの接続をテストしてください。通常の本番環境では接続できるはずです。

#### オプション 2: MongoDB Atlas ネットワーク設定確認
1. MongoDB Atlas Console → Network Access
2. IPアクセスリストの確認
3. 0.0.0.0/0（すべて許可）または特定IPの追加
4. VPC Peering/PrivateLinkの検討

#### オプション 3: SSL/TLS設定の調整
```yaml
# application.yml
spring:
  data:
    mongodb:
      uri: mongodb+srv://user:pass@cluster.mongodb.net/db
      ssl:
        enabled: true
        invalid-host-name-allowed: true
```

#### オプション 4: MongoDB Atlas接続文字列オプション
```
mongodb+srv://user:pass@cluster.mongodb.net/db?
  retryWrites=true&
  w=majority&
  tls=true&
  tlsAllowInvalidCertificates=true&
  tlsAllowInvalidHostnames=true
```
⚠️ 注意: 本番環境では証明書検証を無効化しないでください

## 🎯 結論

### 現状
- ❌ MongoDB Atlasへの直接接続は、このサンドボックス環境では**不可能**
- ✅ ローカルMongoDBを使用することで、アプリケーションは**完全に動作**

### 影響
- **開発・テスト**: 問題なし（ローカルMongoDBで実施可能）
- **本番デプロイ**: 通常の本番環境では問題なく接続できるはず

### 次のステップ
1. 開発・テスト: ローカルMongoDBを継続使用
2. ステージング・本番: 実際のデプロイ環境でMongoDB Atlas接続をテスト
3. 必要に応じて、MongoDB Atlas側のネットワーク設定を確認

## 📌 現在の稼働状態

**アプリケーション**: ✅ 正常稼働中
**MongoDB**: ローカル (mongodb://localhost:27017/yt-orchestrator)
**データベース名**: yt-orchestrator
**ステータス**: UP
**公開URL**: https://8080-i1a9w5dy2umu4imh2nyoc-5185f4aa.sandbox.novita.ai

---

**まとめ**: MongoDB Atlasへの接続は環境制限により失敗しましたが、ローカルMongoDBを使用することで、アプリケーションは完全に動作しています。本番デプロイ時には通常の環境からMongoDB Atlasに接続できるはずです。
