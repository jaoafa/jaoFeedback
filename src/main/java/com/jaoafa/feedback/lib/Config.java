package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

public class Config {
    private final Logger logger;
    private final String token;
    private final long guildId;
    private final long channelId;
    private final Set<String> shouldJoinThreadMentionIds;
    private final String githubAPIToken;
    private final String repository;
    private final long resolvedTagId;
    private final long unresolvedTagId;
    private final boolean issueSyncEnabled;
    private final int issueSyncIntervalSeconds;
    private final String issueSyncStatePath;

    public Config() throws RuntimeException {
        logger = Main.getLogger();

        String path = "config.json";
        if (System.getenv("CONFIG_PATH") != null) {
            path = System.getenv("CONFIG_PATH");
        }
        File file = new File(path);
        if (!file.exists()) {
            logger.error("コンフィグファイル config.json が見つかりません。");
            throw new RuntimeException();
        }

        try {
            String json = String.join("\n", Files.readAllLines(file.toPath()));
            JSONObject config = new JSONObject(json);

            // - 必須項目の定義（ない場合、RuntimeExceptionが発生して進まない）
            requiredConfig(config, "token");
            requiredConfig(config, "guildId");
            requiredConfig(config, "channelId");
            requiredConfig(config, "githubAPIToken");
            requiredConfig(config, "resolvedTagId");
            requiredConfig(config, "unresolvedTagId");

            // - 設定項目の取得
            token = config.getString("token");
            guildId = config.getLong("guildId");
            channelId = config.getLong("channelId");
            githubAPIToken = config.getString("githubAPIToken");
            resolvedTagId = config.getLong("resolvedTagId");
            unresolvedTagId = config.getLong("unresolvedTagId");

            // - 任意項目の取得
            shouldJoinThreadMentionIds = config.optJSONArray("shouldJoinThreadMentionIds", new JSONArray()).toList().stream().map(Object::toString).collect(Collectors.toSet());
            repository = config.optString("repository", "jaoafa/jao-Minecraft-Server");
            issueSyncEnabled = config.optBoolean("issueSyncEnabled", true);
            issueSyncIntervalSeconds = config.optInt("issueSyncIntervalSeconds", 180);
            issueSyncStatePath = config.optString("issueSyncStatePath", "issue_sync_state.json");
        } catch (IOException e) {
            logger.warn("コンフィグファイル config.json を読み取れませんでした: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException();
        } catch (JSONException e) {
            logger.warn("コンフィグファイル config.json の JSON 形式が正しくありません: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    void requiredConfig(JSONObject config, String key) throws RuntimeException {
        if (config.has(key)) {
            return;
        }
        logger.warn(String.format("コンフィグファイルで必須であるキーが見つかりません: %s", key));
        throw new RuntimeException();
    }

    public String getToken() {
        return token;
    }

    public long getGuildId() {
        return guildId;
    }

    public long getChannelId() {
        return channelId;
    }

    public Set<String> getShouldJoinThreadMentionIds() {
        return shouldJoinThreadMentionIds;
    }

    public String getGitHubAPIToken() {
        return githubAPIToken;
    }

    public String getRepository() {
        return repository;
    }

    public long getResolvedTagId() {
        return resolvedTagId;
    }

    public long getUnresolvedTagId() {
        return unresolvedTagId;
    }

    public boolean getIssueSyncEnabled() {
        return issueSyncEnabled;
    }

    public int getIssueSyncIntervalSeconds() {
        return issueSyncIntervalSeconds;
    }

    public String getIssueSyncStatePath() {
        return issueSyncStatePath;
    }
}
