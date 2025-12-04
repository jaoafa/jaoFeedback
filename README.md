# jaoFeedback

jMS Gamers Club 用の不具合報告 / 新機能リクエスト / 機能改善リクエストの管理 Bot

## 設定

`config.json` ファイルで以下の設定が可能です：

### 必須項目
- `token`: Discord Bot トークン
- `guildId`: サーバーID
- `channelId`: フォーラムチャンネルID
- `githubAPIToken`: GitHub API トークン

### 任意項目
- `shouldJoinThreadMentionIds`: スレッドに参加すべきユーザー/ロールのIDリスト
- `repository`: GitHub リポジトリ（デフォルト: `jaoafa/jao-Minecraft-Server`）
- `unresolvedTagId`: 未解決タグのID（設定すると新規フィードバックに自動適用）
- `resolvedTagId`: 解決済みタグのID（設定するとクローズ時に自動適用）

### タグの設定について

`unresolvedTagId` と `resolvedTagId` を設定することで、フィードバックの状態を自動的にタグで管理できます：

1. 新規フィードバック作成時: `unresolvedTagId` のタグが自動適用されます
2. フィードバッククローズ時: `unresolvedTagId` のタグが削除され、`resolvedTagId` のタグが適用されます

タグIDは Discord のフォーラムチャンネルで作成したタグの ID です。タグIDの取得方法については Discord の開発者ドキュメントを参照してください。
