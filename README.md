# jaoFeedback

jMS Gamers Club 用の不具合報告 / 新機能リクエスト / 機能改善リクエストの管理 Bot

## 設定

設定ファイルは JSON 形式で、環境変数 `CONFIG_PATH` で場所を指定できます(未指定時は `config.json`)。

| キー | 必須 | 既定値 | 説明 |
| --- | --- | --- | --- |
| `token` | 必須 | | Discord Bot のトークン |
| `guildId` | 必須 | | 対象サーバーの ID |
| `channelId` | 必須 | | 報告スレッドを作成するフォーラムチャンネルの ID |
| `githubAPIToken` | 必須 | | GitHub API のトークン |
| `resolvedTagId` | 必須 | | クローズ時に付与する「解決済み」タグの ID |
| `unresolvedTagId` | 必須 | | クローズ時に外す「未解決」タグの ID |
| `shouldJoinThreadMentionIds` | 任意 | `[]` | スレッド作成時にメンションするユーザーの ID |
| `repository` | 任意 | `jaoafa/jao-Minecraft-Server` | Issue を作成するリポジトリ |
| `issueSyncEnabled` | 任意 | `true` | GitHub Issue のコメントを Discord に同期するか |
| `issueSyncIntervalSeconds` | 任意 | `180` | 同期の間隔(秒)。30 未満を指定した場合は 30 になります |
| `issueSyncStatePath` | 任意 | `issue_sync_state.json` | 同期状態の保存先 |

## ビルド

```
mvn -B package
```
