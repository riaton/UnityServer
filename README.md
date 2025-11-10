# UnityServer - マッチングAPI

Java Spring Bootで実装されたマッチングシステムのバックエンドAPIサーバー

## 技術スタック

- Java 17
- Spring Boot 3.2.0
- Redis (Elasticache for Redis)
- AWS Cognito (JWT認証)
- WebSocket (Spring WebSocket)

## ローカルでの実行方法

### 前提条件

- Java 17以上がインストールされていること
- Docker Desktopがインストール・起動されていること
- Gradleが利用可能であること（またはGradle Wrapperを使用）

### 1. Redisの起動

DockerでRedisを起動します：

```bash
docker run --name redis-matching -p 6379:6379 -d redis:7
```

Redisが起動しているか確認：

```bash
docker ps | grep redis-matching
```

### 2. 環境変数の設定

ローカル開発用の環境変数を設定します：

```bash
# Redis接続情報
export REDIS_HOST=localhost
export REDIS_PORT=6379

# Cognito設定（本番環境用、ローカル開発では不要）
export COGNITO_USER_POOL_ID=ap-northeast-1_ySe4wHv7r
export COGNITO_REGION=ap-northeast-1

# 開発用: JWT認証をバイパス（ローカル開発時のみ）
export AUTH_BYPASS=true
```

### 3. アプリケーションの起動

#### Gradle Wrapperを使用（推奨）

```bash
cd UnityServer
./gradlew bootRun
```

サーバーは `http://localhost:8080` で起動します。

### 4. 動作確認

#### API①: チームスペース作成

```bash
curl -X POST http://localhost:8080/api/organize_team \
  -H 'Content-Type: application/json' \
  -H 'X-Debug-UserId: user-123' \
  -d '{"userId":"user-123"}'
```

**レスポンス例:**
```json
{"teamspaceId":"550e8400-e29b-41d4-a716-446655440000"}
```

### 5. Redisデータの確認

#### redis-cliで接続

```bash
docker exec -it redis-matching redis-cli
```

#### よく使うコマンド

```bash
# すべてのteamspaceキーを表示
KEYS teamspace:*

# 特定のteamspaceのデータを取得
GET teamspace:<teamspaceId>

# JSONを見やすく整形（jqが必要）
docker exec redis-matching redis-cli GET "teamspace:<teamspaceId>" | jq .

# キーの有効期限を確認（秒単位）
TTL teamspace:<teamspaceId>

# 特定のキーを削除
DEL teamspace:<teamspaceId>

# すべてのキーを削除（注意）
FLUSHALL
```

### 6. クリーンアップ

#### Redisコンテナの停止・削除

```bash
docker stop redis-matching
docker rm redis-matching
```

#### アプリケーションの停止

`Ctrl+C`で停止します。

### 開発用認証バイパスについて

ローカル開発時は、環境変数 `AUTH_BYPASS=true` を設定することでJWT認証をバイパスできます。

この場合、`X-Debug-UserId` ヘッダーで指定したユーザーIDが使用されます（未指定の場合は `local-user` が使用されます）。

**注意**: 本番環境では必ず `AUTH_BYPASS` を設定しないでください。

## 認証

すべてのAPIエンドポイントはCognitoアクセストークンによる認証が必要です。

**Header:**
```
Authorization: Bearer <Cognitoアクセストークン>
```

## ログ

ログは標準出力・標準エラー出力に出力され、ECSタスク定義によりCloudWatch Logsの`/ecs/matching-api`ロググループに自動送信されます。

構造化ログ（JSON形式）も出力されます。

## Docker

```bash
docker build -t unity-server .
docker run -p 8080:8080 \
  -e REDIS_HOST=your-redis-host \
  -e COGNITO_USER_POOL_ID=your-pool-id \
  unity-server
```
