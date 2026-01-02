package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
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
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    String details = "Empty response body";
                    Main.getLogger().error("GitHub.createIssueComment: " + details);
                    return new CreateIssueCommentResult(null, details);
                }
                JSONObject obj = new JSONObject(responseBody.string());
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
                    return new ResolveIssueResult(null, -1, null, null, details);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    String details = "Empty response body";
                    Main.getLogger().error("GitHub.resolveIssue: " + details);
                    return new ResolveIssueResult(null, -1, null, null, details);
                }
                JSONObject obj = new JSONObject(responseBody.string());
                int resolvedIssueNumber = obj.getInt("number");
                String htmlUrl = obj.optString("html_url", null);
                String nodeId = obj.optString("node_id", null);

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

                return new ResolveIssueResult(resolvedRepo, resolvedIssueNumber, htmlUrl, nodeId, null);
            }
        } catch (IOException e) {
            return new ResolveIssueResult(null, -1, null, null, e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record ResolveIssueResult(String repository, int issueNumber, String htmlUrl, String nodeId, String error) {
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

    public static ListRepositoriesResult listOrganizationRepositories(String organization, int limit) {
        if (limit <= 0) {
            return new ListRepositoriesResult(List.of(), "Invalid limit");
        }
        int perPage = Math.min(limit, 100);
        String githubToken = Main.getConfig().getGitHubAPIToken();
        String url = String.format("https://api.github.com/orgs/%s/repos?per_page=%d&sort=updated&direction=desc", organization, perPage);

        try {
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", String.format("token %s", githubToken))
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";
                if (response.code() != 200) {
                    Main.getLogger().error("GitHub.listOrganizationRepositories: " + body);
                    return new ListRepositoriesResult(List.of(), body.isEmpty() ? "Unexpected status: " + response.code() : body);
                }
                JSONArray array = new JSONArray(body);
                List<String> repositories = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.optJSONObject(i);
                    if (obj == null) {
                        continue;
                    }
                    String fullName = obj.optString("full_name", null);
                    if (fullName != null && !fullName.isEmpty()) {
                        repositories.add(fullName);
                    }
                }
                return new ListRepositoriesResult(repositories, null);
            }
        } catch (IOException e) {
            return new ListRepositoriesResult(List.of(), e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record ListRepositoriesResult(List<String> repositories, String error) {
    }

    public static RepositoryNodeIdResult getRepositoryNodeId(String repo) {
        String githubToken = Main.getConfig().getGitHubAPIToken();
        String url = String.format("https://api.github.com/repos/%s", repo);

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
                    Main.getLogger().error("GitHub.getRepositoryNodeId: " + details);
                    return new RepositoryNodeIdResult(null, details);
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    String details = "Empty response body";
                    Main.getLogger().error("GitHub.getRepositoryNodeId: " + details);
                    return new RepositoryNodeIdResult(null, details);
                }
                JSONObject obj = new JSONObject(responseBody.string());
                String nodeId = obj.optString("node_id", null);
                if (nodeId == null || nodeId.isEmpty()) {
                    String details = "Empty repository node_id";
                    Main.getLogger().error("GitHub.getRepositoryNodeId: " + details);
                    return new RepositoryNodeIdResult(null, details);
                }
                return new RepositoryNodeIdResult(nodeId, null);
            }
        } catch (IOException e) {
            return new RepositoryNodeIdResult(null, e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record RepositoryNodeIdResult(String nodeId, String error) {
    }

    public static TransferIssueResult transferIssue(String issueNodeId, String targetRepository, boolean createLabelsIfMissing) {
        RepositoryNodeIdResult repositoryNodeIdResult = getRepositoryNodeId(targetRepository);
        if (repositoryNodeIdResult.error() != null) {
            return new TransferIssueResult(null, null, null, repositoryNodeIdResult.error());
        }

        JSONObject variables = new JSONObject()
                .put("issueId", issueNodeId)
                .put("repositoryId", repositoryNodeIdResult.nodeId())
                .put("createLabelsIfMissing", createLabelsIfMissing);
        String query = "mutation($issueId: ID!, $repositoryId: ID!, $createLabelsIfMissing: Boolean!) {"
                + " transferIssue(input: {issueId: $issueId, repositoryId: $repositoryId, createLabelsIfMissing: $createLabelsIfMissing}) {"
                + " issue { number url repository { nameWithOwner } }"
                + " }"
                + "}";
        JSONObject payload = new JSONObject()
                .put("query", query)
                .put("variables", variables);

        String githubToken = Main.getConfig().getGitHubAPIToken();
        try {
            OkHttpClient client = new OkHttpClient();
            RequestBody requestBody = RequestBody.create(payload.toString(),
                    MediaType.parse("application/json; charset=UTF-8"));
            Request request = new Request.Builder()
                    .url("https://api.github.com/graphql")
                    .header("Authorization", String.format("token %s", githubToken))
                    .post(requestBody)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                ResponseBody responseBody = response.body();
                String body = responseBody != null ? responseBody.string() : "";
                if (response.code() != 200) {
                    Main.getLogger().error("GitHub.transferIssue: " + body);
                    return new TransferIssueResult(null, null, null, body.isEmpty() ? "Unexpected status: " + response.code() : body);
                }
                JSONObject obj = new JSONObject(body);
                if (obj.has("errors")) {
                    JSONArray errors = obj.getJSONArray("errors");
                    StringBuilder detailsBuilder = new StringBuilder();
                    for (int i = 0; i < errors.length(); i++) {
                        JSONObject error = errors.optJSONObject(i);
                        String message = error != null ? error.optString("message", error.toString()) : errors.get(i).toString();
                        if (detailsBuilder.length() != 0) {
                            detailsBuilder.append("\n");
                        }
                        detailsBuilder.append(message);
                    }
                    String details = detailsBuilder.length() == 0 ? "Unknown error" : detailsBuilder.toString();
                    Main.getLogger().error("GitHub.transferIssue: " + details);
                    return new TransferIssueResult(null, null, null, details);
                }
                JSONObject data = obj.optJSONObject("data");
                if (data == null) {
                    String details = "Empty data in GraphQL response";
                    Main.getLogger().error("GitHub.transferIssue: " + details);
                    return new TransferIssueResult(null, null, null, details);
                }
                JSONObject transfer = data.optJSONObject("transferIssue");
                if (transfer == null) {
                    String details = "Empty transferIssue in GraphQL response";
                    Main.getLogger().error("GitHub.transferIssue: " + details);
                    return new TransferIssueResult(null, null, null, details);
                }
                JSONObject issue = transfer.optJSONObject("issue");
                if (issue == null) {
                    String details = "Empty issue in GraphQL response";
                    Main.getLogger().error("GitHub.transferIssue: " + details);
                    return new TransferIssueResult(null, null, null, details);
                }
                int issueNumber = issue.getInt("number");
                String htmlUrl = issue.optString("url", null);
                String repository = null;
                JSONObject repositoryObj = issue.optJSONObject("repository");
                if (repositoryObj != null) {
                    repository = repositoryObj.optString("nameWithOwner", null);
                }
                return new TransferIssueResult(issueNumber, repository, htmlUrl, null);
            }
        } catch (IOException e) {
            return new TransferIssueResult(null, null, null, e.getClass().getName() + " " + e.getMessage());
        }
    }

    public record TransferIssueResult(Integer issueNumber, String repository, String htmlUrl, String error) {
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
