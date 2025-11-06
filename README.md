# UnityServer - マッチングAPI

Java Spring Bootで実装されたマッチングシステムのバックエンドAPIサーバー

## 機能

- **API①**: チームスペース作成
- **API②**: チーム参加
- **API③**: チーム脱退
- **API④**: ゲーム開始
- **WebSocket**: リアルタイム通知（メンバーリスト更新、partyId通知）

## 技術スタック

- Java 17
- Spring Boot 3.2.0
- Redis (Elasticache for Redis)
- AWS Cognito (JWT認証)
- WebSocket (Spring WebSocket)

## 環境変数

以下の環境変数を設定してください：

```bash
# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Cognito
COGNITO_USER_POOL_ID=ap-northeast-1_ySe4wHv7r
COGNITO_REGION=ap-northeast-1
```

## ビルド・起動

### Gradle Wrapperの生成

```bash
cd UnityServer
gradle wrapper --gradle-version 8.5
```

### ビルド

```bash
./gradlew build
```

### 実行

```bash
./gradlew bootRun
```

または

```bash
java -jar build/libs/unity-server-0.0.0-SNAPSHOT.jar
```

サーバーは `http://localhost:8080` で起動します。

## APIエンドポイント

### API① POST /api/organize_team
チームスペースを作成します。

**Request:**
```json
{
  "userId": "user-123"
}
```

**Response (201 Created):**
```json
{
  "teamspaceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### API② POST /api/join_team
チームに参加します。

**Request:**
```json
{
  "teamspaceId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-456"
}
```

**Response (200 OK):**
```json
{}
```

### API③ POST /api/leave_team
チームから脱退します。

**Request:**
```json
{
  "teamspaceId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-456"
}
```

**Response (200 OK):**
```json
{}
```

### API④ POST /api/start_game
ゲームを開始します。

**Request:**
```json
{
  "teamspaceId": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "user-123"
}
```

**Response (200 OK):**
```json
{
  "partyId": "660e8400-e29b-41d4-a716-446655440001"
}
```

## WebSocket

WebSocketエンドポイント: `ws://localhost:8080/ws?teamspaceId={teamspaceId}&userId={userId}`

### メッセージ形式

#### メンバーリスト更新通知
```json
{
  "type": "memberList",
  "userIds": ["user-123", "user-456"]
}
```

#### partyId通知
```json
{
  "type": "partyId",
  "partyId": "660e8400-e29b-41d4-a716-446655440001"
}
```

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
