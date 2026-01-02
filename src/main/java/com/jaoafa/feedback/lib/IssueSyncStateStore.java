package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IssueSyncStateStore {
    private final Path path;

    public IssueSyncStateStore(String path) {
        this.path = Path.of(path);
    }

    public synchronized Map<Long, IssueSyncState> load() {
        Map<Long, IssueSyncState> states = new HashMap<>();
        if (!Files.exists(path)) {
            return states;
        }
        try {
            String content = Files.readString(path);
            if (content.isBlank()) {
                return states;
            }
            JSONArray array = new JSONArray(content);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) {
                    continue;
                }
                IssueSyncState state = IssueSyncState.fromJSON(obj);
                if (state != null) {
                    states.put(state.threadId, state);
                }
            }
        } catch (IOException e) {
            Main.getLogger().error("Failed to read issue sync state: " + e.getMessage());
        }
        return states;
    }

    public synchronized void save(Collection<IssueSyncState> states) {
        JSONArray array = new JSONArray();
        for (IssueSyncState state : states) {
            array.put(state.toJSON());
        }
        try {
            Files.writeString(path, array.toString());
        } catch (IOException e) {
            Main.getLogger().error("Failed to write issue sync state: " + e.getMessage());
        }
    }

    public static class IssueSyncState {
        public long threadId;
        public String repository;
        public int issueNumber;
        public Instant lastCommentUpdatedAt;
        public String lastIssueState;
        public final Map<Long, CommentState> commentMap = new HashMap<>();

        public static IssueSyncState create(long threadId, String repository, int issueNumber, Instant now) {
            IssueSyncState state = new IssueSyncState();
            state.threadId = threadId;
            state.repository = repository;
            state.issueNumber = issueNumber;
            state.lastCommentUpdatedAt = now;
            state.lastIssueState = null;
            return state;
        }

        public void resetForIssue(String repository, int issueNumber, Instant now) {
            this.repository = repository;
            this.issueNumber = issueNumber;
            this.lastCommentUpdatedAt = now;
            this.lastIssueState = null;
            this.commentMap.clear();
        }

        public JSONObject toJSON() {
            JSONObject obj = new JSONObject()
                    .put("threadId", threadId)
                    .put("repository", repository)
                    .put("issueNumber", issueNumber);
            if (lastCommentUpdatedAt != null) {
                obj.put("lastCommentUpdatedAt", lastCommentUpdatedAt.toString());
            }
            if (lastIssueState != null) {
                obj.put("lastIssueState", lastIssueState);
            }
            JSONObject commentMapJson = new JSONObject();
            for (Map.Entry<Long, CommentState> entry : commentMap.entrySet()) {
                commentMapJson.put(entry.getKey().toString(), entry.getValue().toJSON());
            }
            obj.put("commentMap", commentMapJson);
            return obj;
        }

        public static IssueSyncState fromJSON(JSONObject obj) {
            long threadId = obj.optLong("threadId", -1);
            if (threadId <= 0) {
                return null;
            }
            String repository = obj.optString("repository", null);
            int issueNumber = obj.optInt("issueNumber", -1);

            IssueSyncState state = new IssueSyncState();
            state.threadId = threadId;
            state.repository = repository;
            state.issueNumber = issueNumber;
            state.lastCommentUpdatedAt = parseInstant(obj.optString("lastCommentUpdatedAt", null));
            state.lastIssueState = obj.optString("lastIssueState", null);
            JSONObject commentMapJson = obj.optJSONObject("commentMap");
            if (commentMapJson != null) {
                for (String key : commentMapJson.keySet()) {
                    JSONObject value = commentMapJson.optJSONObject(key);
                    if (value == null) {
                        continue;
                    }
                    try {
                        long commentId = Long.parseLong(key);
                        CommentState commentState = CommentState.fromJSON(value);
                        if (commentState != null) {
                            state.commentMap.put(commentId, commentState);
                        }
                    } catch (NumberFormatException ignored) {
                        // Skip invalid keys.
                    }
                }
            }
            return state;
        }
    }

    public static class CommentState {
        public long messageId;
        public Instant updatedAt;

        public JSONObject toJSON() {
            JSONObject obj = new JSONObject()
                    .put("messageId", messageId);
            if (updatedAt != null) {
                obj.put("updatedAt", updatedAt.toString());
            }
            return obj;
        }

        public static CommentState fromJSON(JSONObject obj) {
            long messageId = obj.optLong("messageId", -1);
            if (messageId <= 0) {
                return null;
            }
            CommentState state = new CommentState();
            state.messageId = messageId;
            state.updatedAt = parseInstant(obj.optString("updatedAt", null));
            return state;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
