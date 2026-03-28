package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.lib.GitHub;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;

public class ChangeTitleSubmitEvent extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getModalId().equals("change-title")) {
            return;
        }
        String newTitle = Objects.requireNonNull(event.getValue("new-title")).getAsString();

        User user = event.getUser();
        ThreadChannel thread = FeedbackManager.changeTitleMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        FeedbackManager.changeTitleMap.remove(user.getIdLong());

        event.deferEdit().queue();

        int issueNumber = -1;
        String prevTitle = thread.getName();
        Matcher matcher = FeedbackManager.ISSUE_PATTERN.matcher(prevTitle);
        if (matcher.find()) {
            issueNumber = Integer.parseInt(matcher.group(1));
        }
        int originalIssueNumber = issueNumber;

        FeedbackManager feedbackManager = new FeedbackManager();
        String repository = Main.getConfig().getRepository();
        FeedbackManager.Feedback feedback = feedbackManager.getFeedbackByThreadId(thread.getIdLong());
        if (feedback != null && feedback.repository() != null) {
            repository = feedback.repository();
        }
        String originalRepository = repository;

        GitHub.ResolveIssueResult resolved = null;
        if (issueNumber != -1) {
            resolved = GitHub.resolveIssue(repository, issueNumber);
            if (resolved.error() == null) {
                if (resolved.repository() != null) {
                    repository = resolved.repository();
                }
                issueNumber = resolved.issueNumber();
            }
        }

        String threadTitle = issueNumber == -1 ? newTitle : "*%d %s".formatted(issueNumber, newTitle);

        thread.sendMessage("`%s` のアクションにより、タイトルを変更します。".formatted(user.getName())).complete();
        thread.getManager().setName(threadTitle).queue();

        if (issueNumber == -1) {
            return;
        }
        if (resolved != null && resolved.error() != null) {
            String errorResult = resolved.error();
            if (errorResult.length() > 100) {
                errorResult = errorResult.substring(0, 100) + "...";
            }
            thread.sendMessage("GitHub Issue の解決に失敗しました:\n```\n%s\n```".formatted(errorResult)).complete();
            return;
        }
        if (resolved != null && (resolved.issueNumber() != originalIssueNumber ||
                (resolved.repository() != null && !resolved.repository().equals(originalRepository)))) {
            feedbackManager.updateFeedbackIssue(thread.getIdLong(), repository, issueNumber);
        }
        GitHub.UpdateIssueResult result = GitHub.updateIssue(repository, issueNumber, GitHub.UpdateType.TITLE, newTitle);
        if (result.error() != null) {
            String errorResult = result.error();
            if (errorResult.length() > 100) {
                errorResult = errorResult.substring(0, 100) + "...";
            }
            thread.sendMessage("GitHubへのタイトルの変更に失敗しました:\n```\n%s\n```".formatted(errorResult)).complete();
        }
    }
}
