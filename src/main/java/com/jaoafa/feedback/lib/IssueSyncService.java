package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IssueSyncService implements Runnable {
    private static final int EMBED_DESCRIPTION_LIMIT = 3800;
    private static final Pattern DISCORD_MESSAGE_MARKER = Pattern.compile("`.+` によるメッセージ");

    private final IssueSyncStateStore stateStore;

    public IssueSyncService(IssueSyncStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Override
    public void run() {
        try {
            sync();
        } catch (Exception e) {
            Main.getLogger().error("Issue sync failed: " + e.getMessage());
        }
    }

    private void sync() {
        FeedbackManager feedbackManager = Main.getFeedbackManager();
        List<FeedbackManager.Feedback> feedbacks = feedbackManager.listFeedbacks();
        if (feedbacks.isEmpty()) {
            return;
        }

        Map<Long, IssueSyncStateStore.IssueSyncState> states = stateStore.load();
        boolean updated = false;
        Instant now = Instant.now();

        GitHub.AuthenticatedUserResult userResult = GitHub.getAuthenticatedUserLogin();
        String botLogin = userResult.error() == null ? userResult.login() : null;
        if (userResult.error() != null) {
            Main.getLogger().warn("Failed to fetch GitHub authenticated user: " + userResult.error());
        }
        Map<Long, ThreadChannel> activeThreads = fetchActiveThreads(Main.getJDA());

        for (FeedbackManager.Feedback feedback : feedbacks) {
            String repository = feedback.repository();
            if (repository == null || repository.isBlank()) {
                repository = Main.getConfig().getRepository();
            }
            int issueNumber = feedback.issueNumber();
            long threadId = feedback.threadId();

            ThreadChannel thread = resolveThread(Main.getJDA(), threadId, activeThreads);
            if (thread == null) {
                continue;
            }
            if (thread.isArchived()) {
                continue;
            }

            IssueSyncStateStore.IssueSyncState state = states.get(threadId);
            if (state == null) {
                state = IssueSyncStateStore.IssueSyncState.create(threadId, repository, issueNumber, now);
                states.put(threadId, state);
                updated = true;
            }
            if (!Objects.equals(state.repository, repository) || state.issueNumber != issueNumber) {
                state.resetForIssue(repository, issueNumber, now);
                updated = true;
            }

            GitHub.IssueResult issueResult = GitHub.getIssue(repository, issueNumber);
            if (issueResult.error() != null) {
                Main.getLogger().warn("Failed to resolve issue state: " + issueResult.error());
                continue;
            }

            String currentState = issueResult.state();
            boolean wasClosed = "closed".equalsIgnoreCase(state.lastIssueState);
            if ("closed".equalsIgnoreCase(currentState)) {
                if (!wasClosed) {
                    notifyIssueClosed(thread, issueResult);
                    applyResolvedTags(thread);
                    thread.getManager().setArchived(true).setLocked(true).queue();
                }
                state.lastIssueState = "closed";
                updated = true;
                continue;
            }

            if (currentState != null && !currentState.isBlank() &&
                    (state.lastIssueState == null || !currentState.equalsIgnoreCase(state.lastIssueState))) {
                state.lastIssueState = currentState;
                updated = true;
            }

            if (thread.isLocked()) {
                continue;
            }

            if (state.lastCommentUpdatedAt == null) {
                state.lastCommentUpdatedAt = now;
                updated = true;
                continue;
            }

            String since = state.lastCommentUpdatedAt.toString();
            GitHub.ListIssueCommentsResult commentsResult = GitHub.listIssueComments(repository, issueNumber, since);
            if (commentsResult.error() != null) {
                Main.getLogger().warn("Failed to list issue comments: " + commentsResult.error());
                continue;
            }

            List<GitHub.IssueComment> comments = new ArrayList<>(commentsResult.comments());
            comments.sort(Comparator.comparing(comment -> parseInstant(comment.updatedAt()), Comparator.nullsLast(Comparator.naturalOrder())));

            Instant maxUpdatedAt = state.lastCommentUpdatedAt;
            for (GitHub.IssueComment comment : comments) {
                if (shouldSkipComment(comment, botLogin)) {
                    continue;
                }
                Instant updatedAt = parseInstant(comment.updatedAt());
                IssueSyncStateStore.CommentState commentState = state.commentMap.get(comment.id());
                if (commentState == null) {
                    Message message = thread.sendMessageEmbeds(buildIssueCommentEmbed(comment, false)).complete();
                    IssueSyncStateStore.CommentState newState = new IssueSyncStateStore.CommentState();
                    newState.messageId = message.getIdLong();
                    newState.updatedAt = updatedAt;
                    state.commentMap.put(comment.id(), newState);
                    updated = true;
                } else if (updatedAt != null && (commentState.updatedAt == null || updatedAt.isAfter(commentState.updatedAt))) {
                    boolean edited = editIssueCommentMessage(thread, commentState.messageId, buildIssueCommentEmbed(comment, true));
                    if (!edited) {
                        Message message = thread.sendMessageEmbeds(buildIssueCommentEmbed(comment, true)).complete();
                        commentState.messageId = message.getIdLong();
                    }
                    commentState.updatedAt = updatedAt;
                    updated = true;
                }
                if (updatedAt != null && (maxUpdatedAt == null || updatedAt.isAfter(maxUpdatedAt))) {
                    maxUpdatedAt = updatedAt;
                }
            }
            if (maxUpdatedAt != null && (state.lastCommentUpdatedAt == null || maxUpdatedAt.isAfter(state.lastCommentUpdatedAt))) {
                state.lastCommentUpdatedAt = maxUpdatedAt;
                updated = true;
            }
        }

        if (updated) {
            stateStore.save(states.values());
        }
    }

    private ThreadChannel resolveThread(JDA jda, long threadId, Map<Long, ThreadChannel> activeThreads) {
        ThreadChannel thread = jda.getThreadChannelById(threadId);
        if (thread != null) {
            return thread;
        }
        if (activeThreads != null) {
            return activeThreads.get(threadId);
        }
        return null;
    }

    private Map<Long, ThreadChannel> fetchActiveThreads(JDA jda) {
        Guild guild = jda.getGuildById(Main.getConfig().getGuildId());
        if (guild == null) {
            return null;
        }
        try {
            List<ThreadChannel> threads = guild.retrieveActiveThreads().complete();
            return threads.stream().collect(Collectors.toMap(ThreadChannel::getIdLong, thread -> thread, (a, b) -> a));
        } catch (ErrorResponseException e) {
            if (e.getErrorResponse() != ErrorResponse.UNKNOWN_CHANNEL && e.getErrorResponse() != ErrorResponse.MISSING_ACCESS) {
                Main.getLogger().warn("Failed to retrieve active threads: " + e.getMessage());
            }
        } catch (Exception e) {
            Main.getLogger().warn("Failed to retrieve active threads: " + e.getMessage());
        }
        return null;
    }

    private void notifyIssueClosed(ThreadChannel thread, GitHub.IssueResult issueResult) {
        if (thread.isLocked()) {
            return;
        }
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle("GitHub Issue がクローズされました")
                .setDescription("GitHub 側で Issue がクローズされたため、このスレッドをクローズします。")
                .setColor(Color.RED);
        if (issueResult.htmlUrl() != null) {
            builder.addField("URL", issueResult.htmlUrl(), false);
        }
        Instant updatedAt = parseInstant(issueResult.updatedAt());
        if (updatedAt != null) {
            builder.setTimestamp(updatedAt);
        }
        thread.sendMessageEmbeds(builder.build()).queue();
    }

    private void applyResolvedTags(ThreadChannel thread) {
        long resolvedTagId = Main.getConfig().getResolvedTagId();
        long unresolvedTagId = Main.getConfig().getUnresolvedTagId();
        List<ForumTag> currentTags = thread.getAppliedTags().stream()
                .filter(tag -> tag.getIdLong() != unresolvedTagId)
                .collect(Collectors.toCollection(ArrayList::new));
        ForumTag resolvedTag = thread.getParentChannel().asForumChannel().getAvailableTagById(resolvedTagId);
        if (resolvedTag != null && currentTags.stream().noneMatch(tag -> tag.getIdLong() == resolvedTagId)) {
            currentTags.add(resolvedTag);
        }
        thread.getManager().setAppliedTags(currentTags).queue();
    }

    private boolean shouldSkipComment(GitHub.IssueComment comment, String botLogin) {
        if (botLogin != null && comment.userLogin() != null && botLogin.equalsIgnoreCase(comment.userLogin())) {
            return true;
        }
        if (botLogin == null && comment.body() != null && DISCORD_MESSAGE_MARKER.matcher(comment.body()).find()) {
            return true;
        }
        return false;
    }

    private MessageEmbed buildIssueCommentEmbed(GitHub.IssueComment comment, boolean edited) {
        String description = comment.body() == null || comment.body().isBlank() ? "（本文なし）" : comment.body();
        description = truncate(description, EMBED_DESCRIPTION_LIMIT);

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(edited ? "GitHub Issue コメント (編集)" : "GitHub Issue コメント")
                .setDescription(description)
                .setColor(edited ? Color.ORANGE : Color.GREEN);

        if (comment.htmlUrl() != null) {
            builder.addField("URL", comment.htmlUrl(), false);
        }
        if (comment.userLogin() != null) {
            builder.setAuthor(comment.userLogin(), "https://github.com/" + comment.userLogin(), null);
        }
        Instant updatedAt = parseInstant(comment.updatedAt());
        if (updatedAt != null) {
            builder.setTimestamp(updatedAt);
        }
        return builder.build();
    }

    private boolean editIssueCommentMessage(ThreadChannel thread, long messageId, MessageEmbed embed) {
        try {
            thread.editMessageById(messageId, MessageEditData.fromEmbeds(embed)).complete();
            return true;
        } catch (Exception e) {
            Main.getLogger().warn("Failed to edit comment message: " + e.getMessage());
            return false;
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
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
