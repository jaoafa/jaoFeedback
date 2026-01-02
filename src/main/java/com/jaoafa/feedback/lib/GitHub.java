package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GitHub {
    public static CreateIssueResult createIssue(String repo, String title, String body, JSONArray labels) {
        String githubToken = Main.getConfig().getGitHubAPIToken();
        String url = String.format("https://api.github.com/repos/%s/issues", repo);
        JSONObject json = new JSONObject()
                .put("title", title)
                .put("body", body)
                .put("labels", labels);

        try {
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(json.toString(),
                                                         MediaType.parse("application/json; charset=UTF-8"));
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("token %s", githubToken))
                .post(requestBody)
                .build();
            JSONObject obj;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200 && response.code() != 201) {
                    ResponseBody result = response.body();
                    String details = result.string();
                    Main.getLogger().error("GitHub.createIssue: " + details);
                    return new CreateIssueResult(-1, details);
                }
                obj = new JSONObject(Objects.requireNonNull(response.body()).string());
            }
            int issueNum = obj.getInt("number");
            return new CreateIssueResult(issueNum, null);
        } catch (IOException e) {
            return new CreateIssueResult(-1, e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public record CreateIssueResult(int issueNumber, String error) {
    }

    @NotNull
    public static CreateIssueCommentResult createIssueComment(String repo, int issueNum, String body) {
        String githubToken = Main.getConfig().getGitHubAPIToken();
        String url = String.format("https://api.github.com/repos/%s/issues/%s/comments", repo, issueNum);
        JSONObject json = new JSONObject().put("body", body);

        try {
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(json.toString(),
                                                         MediaType.parse("application/json; charset=UTF-8"));
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", String.format("token %s", githubToken))
                    .post(requestBody)
                    .build();
            String htmlUrl;
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200 && response.code() != 201) {
                    ResponseBody result = response.body();
                    String details = result.string();
                    Main.getLogger().error("GitHub.createIssueComment: " + details);
                    return new CreateIssueCommentResult(null, details);
                }
                JSONObject obj = new JSONObject(Objects.requireNonNull(response.body()).string());
                htmlUrl = obj.getString("html_url");
            }

            return new CreateIssueCommentResult(htmlUrl, null);
        } catch (IOException e) {
            e.printStackTrace();
            return new CreateIssueCommentResult(null, e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record CreateIssueCommentResult(String htmlUrl, String error) {
    }

    public static ResolveIssueResult resolveIssue(String repo, int issueNum) {
        String githubToken = Main.getConfig().getGitHubAPIToken();
        String url = String.format("https://api.github.com/repos/%s/issues/%s", repo, issueNum);

        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", String.format("token %s", githubToken))
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200) {
                    ResponseBody result = response.body();
                    String details = result != null ? result.string() : "";
                    Main.getLogger().error("GitHub.resolveIssue: " + details);
                    return new ResolveIssueResult(null, -1, null, details);
                }

                JSONObject obj = new JSONObject(Objects.requireNonNull(response.body()).string());
                int resolvedIssueNumber = obj.getInt("number");
                String htmlUrl = obj.optString("html_url", null);

                String resolvedRepo = null;
                HttpUrl finalUrl = response.request().url();
                List<String> segments = finalUrl.pathSegments();
                if (segments.size() >= 5 && "repos".equals(segments.get(0)) && "issues".equals(segments.get(3))) {
                    resolvedRepo = segments.get(1) + "/" + segments.get(2);
                }
                if (resolvedRepo == null && htmlUrl != null) {
                    try {
                        URI uri = URI.create(htmlUrl);
                        String[] parts = uri.getPath().split("/");
                        if (parts.length >= 3) {
                            resolvedRepo = parts[1] + "/" + parts[2];
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Leave resolvedRepo as null.
                    }
                }

                return new ResolveIssueResult(resolvedRepo, resolvedIssueNumber, htmlUrl, null);
            }
        } catch (IOException e) {
            return new ResolveIssueResult(null, -1, null, e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record ResolveIssueResult(String repository, int issueNumber, String htmlUrl, String error) {
    }

    public static UpdateIssueResult updateIssue(String repo, int issueNum, UpdateType type, Object value) {
        String githubToken = Main.getConfig().getGitHubAPIToken();
        String url = String.format("https://api.github.com/repos/%s/issues/%s", repo, issueNum);
        JSONObject json = new JSONObject().put(type.name().toLowerCase(Locale.ROOT), value);

        try {
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(json.toString(),
                                                         MediaType.parse("application/json; charset=UTF-8"));
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", String.format("token %s", githubToken))
                .post(requestBody)
                .build();
            try (Response response = client.newCall(request).execute()) {
                if (response.code() != 200 && response.code() != 201) {
                    ResponseBody result = response.body();
                    String details = result.string();
                    Main.getLogger().error("GitHub.updateIssue: " + details);
                    return new UpdateIssueResult(details);
                }
            }

            return new UpdateIssueResult(null);
        } catch (IOException e) {
            e.printStackTrace();
            return new UpdateIssueResult(e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record UpdateIssueResult(String error) {
    }

    public enum UpdateType {
        TITLE,
        BODY,
        @Deprecated
        ASSIGNEE,
        STATE,
        MILESTONE,
        LABELS,
        ASSIGNEES
    }
}
