package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumPost;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

public class FeedbackManager {
    public static final Map<Long, Message> messageMap = new HashMap<>();
    public static final Map<Long, ThreadChannel> changeTitleMap = new HashMap<>();
    public static final Map<Long, ThreadChannel> sendToIssueMap = new HashMap<>();
    public static final Map<Long, ThreadChannel> transferIssueMap = new HashMap<>();
    public static final Map<Long, ThreadChannel> closeReportMap = new HashMap<>();
    public static final Pattern ISSUE_PATTERN = Pattern.compile("^\\*(\\d+) ");
    private final Path FEEDBACKS_PATH;

    public FeedbackManager() {
        FEEDBACKS_PATH = System.getenv("FEEDBACKS_PATH") != null ?
                Path.of(System.getenv("FEEDBACKS_PATH")) :
                Path.of("feedbacks.json");
    }

    public ForumPost createFeatureRequest(@Nullable Message message,
                                          @NotNull User requester,
                                          @NotNull String title,
                                          @NotNull String description) throws FeedbackException {
        // Issueを作成
        String githubBody = FeedbackModel.getFeatureRequestIssueBody(description, message, requester);

        String repository = Main.getConfig().getRepository();
        GitHub.CreateIssueResult createIssueResult = GitHub.createIssue(repository, title, githubBody, new JSONArray().put("✨enhancement"));

        // スレッドを作成
        JDA jda = Main.getJDA();
        Config config = Main.getConfig();
        ForumChannel channel = jda.getForumChannelById(config.getChannelId());
        if (channel == null) {
            throw new FeedbackException("フォーラムチャンネルを見つけられませんでした。");
        }

        String threadTitle = getThreadTitle(title, createIssueResult);
        MessageEmbed embed = FeedbackModel.getFeatureRequestEmbed(description, message, requester, createIssueResult);
        MessageCreateData forumStartMessage = getForumStartMessage(message, requester, embed, createIssueResult);
        ForumPost forum = createForumPostWithUnresolvedTag(channel, threadTitle, forumStartMessage);
        
        saveFeedback(new Feedback(FeedbackType.FEATURE_REQUEST,
                message != null ? message.getIdLong() : -1,
                new FeedbackUser(requester.getIdLong(), requester.getName()),
                forum.getThreadChannel().getIdLong(),
                repository,
                createIssueResult.issueNumber()));
        return forum;
    }

    public ForumPost createImprovementRequest(@Nullable Message message,
                                              @NotNull User requester,
                                              @NotNull String title,
                                              @NotNull String target,
                                              @NotNull String description) throws FeedbackException {
        // Issueを作成
        String githubBody = FeedbackModel.getImprovementRequestIssueBody(description, target, message, requester);

        String repository = Main.getConfig().getRepository();
        GitHub.CreateIssueResult createIssueResult = GitHub.createIssue(repository, title, githubBody, new JSONArray().put("♻improvement"));

        // スレッドを作成
        JDA jda = Main.getJDA();
        Config config = Main.getConfig();
        ForumChannel channel = jda.getForumChannelById(config.getChannelId());
        if (channel == null) {
            throw new FeedbackException("フォーラムチャンネルを見つけられませんでした。");
        }

        String threadTitle = getThreadTitle(title, createIssueResult);
        MessageEmbed embed = FeedbackModel.getImprovementRequestEmbed(description, target, message, requester, createIssueResult);
        MessageCreateData forumStartMessage = getForumStartMessage(message, requester, embed, createIssueResult);
        ForumPost forum = createForumPostWithUnresolvedTag(channel, threadTitle, forumStartMessage);
        
        saveFeedback(new Feedback(FeedbackType.IMPROVEMENT_REQUEST,
                message != null ? message.getIdLong() : -1,
                new FeedbackUser(requester.getIdLong(), requester.getName()),
                forum.getThreadChannel().getIdLong(),
                repository,
                createIssueResult.issueNumber()));
        return forum;
    }

    public ForumPost createBugReport(@Nullable Message message,
                                     @NotNull User reporter,
                                     @Nullable String inputTitle,
                                     @Nullable String inputDescription) throws FeedbackException {
        String title = inputTitle;
        String description;
        if (inputTitle == null && message != null) {
            title = "%s による #%s での不具合報告".formatted(reporter.getName(), message.getChannel().getName());
        }
        if (title == null && message == null) {
            title = "%s による不具合報告".formatted(reporter.getName());
        }
        description = Objects.requireNonNullElse(inputDescription, "NULL");

        // Issueを作成
        String githubBody = FeedbackModel.getBugReportIssueBody(description, message, reporter);

        String repository = Main.getConfig().getRepository();
        GitHub.CreateIssueResult createIssueResult = GitHub.createIssue(repository, title, githubBody, new JSONArray().put("\uD83D\uDC1Bbug"));

        // スレッドを作成
        JDA jda = Main.getJDA();
        Config config = Main.getConfig();
        ForumChannel channel = jda.getForumChannelById(config.getChannelId());
        if (channel == null) {
            throw new FeedbackException("フォーラムチャンネルを見つけられませんでした。");
        }

        String threadTitle = getThreadTitle(title, createIssueResult);
        MessageEmbed embed = FeedbackModel.getBugReportEmbed(description, message, reporter, createIssueResult);
        MessageCreateData forumStartMessage = getForumStartMessage(message, reporter, embed, createIssueResult);
        ForumPost forum = createForumPostWithUnresolvedTag(channel, threadTitle, forumStartMessage);

        if (inputTitle == null) {
            // リアクションなどでタイトルがnullの場合、詳細情報を示してもらえるようメッセージを送る
            forum.getThreadChannel().sendMessage(getNeedDetailsReplyMessage(reporter)).queue();
        }

        saveFeedback(new Feedback(FeedbackType.BUG_REPORT,
                message != null ? message.getIdLong() : -1,
                new FeedbackUser(reporter.getIdLong(), reporter.getName()),
                forum.getThreadChannel().getIdLong(),
                repository,
                createIssueResult.issueNumber()));
        return forum;
    }

    private String getThreadTitle(String title, GitHub.CreateIssueResult createIssueResult) {
        return (createIssueResult.error() == null ?
                "*" + createIssueResult.issueNumber() + " " :
                "") + title;
    }

    private MessageCreateData getForumStartMessage(@Nullable Message message, User user, MessageEmbed embed, GitHub.CreateIssueResult createIssueResult) {
        List<String> messages = new ArrayList<>();
        if (createIssueResult.error() == null) {
            // Issueが作成できた場合はリンクさせる
            String repository = Main.getConfig().getRepository();
            messages.add("[LINKED-ISSUE:%s#%d]".formatted(repository, createIssueResult.issueNumber()));
            messages.add("");
        }

        messages.add(FeedbackModel.getMentionContent(user, message));

        return new MessageCreateBuilder()
                .setContent(String.join("\n", messages))
                .setEmbeds(embed)
                .addComponents(FeedbackModel.FeedbackActionRow)
                .setSuppressedNotifications(true)
                .build();
    }

    private MessageCreateData getNeedDetailsReplyMessage(User user) {
        return new MessageCreateBuilder()
                .setContent("<@" + user.getId() + "> この報告には詳細情報が含まれていません。このメッセージについて、なぜ不具合だと思ったか、改善策などについて投稿いただけませんか？")
                .mention(user)
                .build();
    }

    private ForumPost createForumPostWithUnresolvedTag(ForumChannel channel, String threadTitle, MessageCreateData forumStartMessage) {
        long unresolvedTagId = Main.getConfig().getUnresolvedTagId();
        ForumTag unresolvedTag = channel.getAvailableTagById(unresolvedTagId);
        
        var builder = channel.createForumPost(threadTitle, forumStartMessage);
        if (unresolvedTag != null) {
            builder.setTags(unresolvedTag);
        }
        return builder.complete();
    }

    public boolean isAlreadyFeedback(Message message) {
        return getFeedback(message, null) != null;
    }

    public Feedback getFeedback(Message message, @Nullable FeedbackType type) {
        List<Feedback> reports = loadReports();
        if (reports == null) {
            return null;
        }
        return reports
                .stream()
                .filter(r -> type == null || r.type() == type)
                .filter(r -> r.messageId == message.getIdLong())
                .findAny()
                .orElse(null);
    }

    public Feedback getFeedbackByThreadId(long threadId) {
        List<Feedback> reports = loadReports();
        if (reports == null) {
            return null;
        }
        return reports
                .stream()
                .filter(r -> r.threadId() == threadId)
                .findAny()
                .orElse(null);
    }

    public synchronized boolean updateFeedbackIssue(long threadId, String repository, int issueNumber) {
        try {
            JSONArray array = new JSONArray();
            if (Files.exists(FEEDBACKS_PATH)) {
                array = new JSONArray(Files.readString(FEEDBACKS_PATH));
            }
            JSONArray newArray = new JSONArray();
            boolean updated = false;
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Feedback feedback = Feedback.fromJSON(obj);
                if (feedback.threadId() == threadId) {
                    feedback = new Feedback(feedback.type(), feedback.messageId(), feedback.reporter(), feedback.threadId(), repository, issueNumber);
                    updated = true;
                }
                newArray.put(feedback.toJSON());
            }
            if (updated) {
                Files.writeString(FEEDBACKS_PATH, newArray.toString());
            }
            return updated;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<Feedback> loadReports() {
        try {
            List<Feedback> reports = new ArrayList<>();
            if (Files.exists(FEEDBACKS_PATH)) {
                JSONArray array = new JSONArray(Files.readString(FEEDBACKS_PATH));
                for (int i = 0; i < array.length(); i++) {
                    reports.add(Feedback.fromJSON(array.getJSONObject(i)));
                }
            }
            return reports;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveFeedback(Feedback report) {
        try {
            JSONArray array = new JSONArray();
            if (Files.exists(FEEDBACKS_PATH)) {
                array = new JSONArray(Files.readString(FEEDBACKS_PATH));
            }
            array.put(report.toJSON());
            Files.writeString(FEEDBACKS_PATH, array.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public record Feedback(FeedbackType type, long messageId, FeedbackUser reporter, long threadId, String repository, int issueNumber) {
        JSONObject toJSON() {
            return new JSONObject()
                    .put("type", type)
                    .put("messageId", messageId)
                    .put("reporter", reporter.toJSON())
                    .put("threadId", threadId)
                    .put("repository", repository)
                    .put("issueNumber", issueNumber);
        }

        static Feedback fromJSON(JSONObject json) {
            String repository = json.optString("repository", Main.getConfig().getRepository());
            return new Feedback(json.getEnum(FeedbackType.class, "type"),
                    json.getLong("messageId"),
                    FeedbackUser.fromJSON(json.getJSONObject("reporter")),
                    json.getLong("threadId"),
                    repository,
                    json.getInt("issueNumber"));
        }
    }

    record FeedbackUser(long userId, String username) {
        JSONObject toJSON() {
            return new JSONObject().put("userId", userId).put("username", username);
        }

        static FeedbackUser fromJSON(JSONObject json) {
            return new FeedbackUser(json.getLong("userId"), json.getString("username"));
        }
    }

    public static class FeedbackException extends Exception {
        FeedbackException(String message) {
            super(message);
        }
    }
}
