package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.lib.GitHub;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransferIssueEvent extends ListenerAdapter {
    private static final Pattern OWNER_REPO_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$");
    private static final Pattern REPO_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]+$");

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getModalId().equals("transfer-issue")) {
            return;
        }

        String rawTarget = Objects.requireNonNull(event.getValue("repository")).getAsString().trim();
        String targetRepository = normalizeTargetRepository(rawTarget);
        if (targetRepository == null) {
            event.reply("移動先リポジトリの形式が正しくありません。`repo` または `owner/repo` で指定してください。").setEphemeral(true).queue();
            return;
        }

        User user = event.getUser();
        ThreadChannel thread = FeedbackManager.transferIssueMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        FeedbackManager.transferIssueMap.remove(user.getIdLong());

        TransferOutcome outcome = transferIssue(user, thread, targetRepository);
        if (!outcome.success()) {
            event.reply(outcome.errorMessage()).setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
    }

    public static TransferOutcome transferIssue(User user, ThreadChannel thread, String targetRepository) {
        int issueNumber = -1;
        String prevTitle = thread.getName();
        Matcher matcher = FeedbackManager.ISSUE_PATTERN.matcher(prevTitle);
        if (matcher.find()) {
            issueNumber = Integer.parseInt(matcher.group(1));
        }
        if (issueNumber == -1) {
            return new TransferOutcome(false, "Issue番号が見つかりませんでした。");
        }

        FeedbackManager feedbackManager = new FeedbackManager();
        String repository = Main.getConfig().getRepository();
        FeedbackManager.Feedback feedback = feedbackManager.getFeedbackByThreadId(thread.getIdLong());
        if (feedback != null && feedback.repository() != null) {
            repository = feedback.repository();
        }
        String originalRepository = repository;

        GitHub.ResolveIssueResult resolved = GitHub.resolveIssue(repository, issueNumber);
        if (resolved.error() != null) {
            String errorResult = trimError(resolved.error(), 100);
            return new TransferOutcome(false, "GitHub Issue の解決に失敗しました: ```%s```".formatted(errorResult));
        }
        if (resolved.repository() != null) {
            repository = resolved.repository();
        }
        if (resolved.issueNumber() != issueNumber || (resolved.repository() != null && !resolved.repository().equals(originalRepository))) {
            String baseTitle = thread.getName().replaceFirst("^\\*\\d+ ", "");
            thread.getManager().setName("*%d %s".formatted(resolved.issueNumber(), baseTitle)).queue();
            feedbackManager.updateFeedbackIssue(thread.getIdLong(), repository, resolved.issueNumber());
        }
        issueNumber = resolved.issueNumber();

        if (resolved.nodeId() == null) {
            return new TransferOutcome(false, "Issue ID の取得に失敗しました。しばらくしてから再試行してください。");
        }
        if (repository != null && repository.equalsIgnoreCase(targetRepository)) {
            return new TransferOutcome(false, "移動先が現在のリポジトリと同一です。");
        }

        GitHub.TransferIssueResult transferResult = GitHub.transferIssue(resolved.nodeId(), targetRepository, false);
        if (transferResult.error() != null) {
            String errorResult = trimError(transferResult.error(), 150);
            return new TransferOutcome(false, "Issue の移動に失敗しました: ```%s```".formatted(errorResult));
        }
        if (transferResult.issueNumber() == null) {
            return new TransferOutcome(false, "Issue の移動結果を取得できませんでした。");
        }

        String baseTitle = thread.getName().replaceFirst("^\\*\\d+ ", "");
        String newRepository = transferResult.repository() != null ? transferResult.repository() : targetRepository;
        thread.getManager().setName("*%d %s".formatted(transferResult.issueNumber(), baseTitle)).queue();
        feedbackManager.updateFeedbackIssue(thread.getIdLong(), newRepository, transferResult.issueNumber());

        thread.sendMessageEmbeds(
                new EmbedBuilder()
                        .setTitle("Issueを移動しました")
                        .addField("移動先", newRepository, false)
                        .addField("URL", transferResult.htmlUrl() != null ? transferResult.htmlUrl() : "N/A", false)
                        .setAuthor(user.getName(), "https://discord.com/users/%s".formatted(user.getId()), user.getAvatarUrl())
                        .setColor(Color.GREEN)
                        .setFooter("GitHub Issue を移動")
                        .build()
        ).queue();

        return new TransferOutcome(true, null);
    }

    private static String normalizeTargetRepository(String targetRepository) {
        if (OWNER_REPO_PATTERN.matcher(targetRepository).matches()) {
            return targetRepository;
        }
        if (REPO_PATTERN.matcher(targetRepository).matches()) {
            return getDefaultOwner() + "/" + targetRepository;
        }
        return null;
    }

    private static String getDefaultOwner() {
        String repository = Main.getConfig().getRepository();
        if (repository != null && repository.contains("/")) {
            return repository.split("/", 2)[0];
        }
        return "jaoafa";
    }

    private static String trimError(String error, int maxLength) {
        if (error == null) {
            return "";
        }
        return error.length() > maxLength ? error.substring(0, maxLength) + "..." : error;
    }

    public record TransferOutcome(boolean success, String errorMessage) {
    }
}
