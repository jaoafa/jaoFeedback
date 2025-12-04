package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.lib.GitHub;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.forums.ForumTag;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;

public class CloseReportEvent extends ListenerAdapter {
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getModalId().equals("close-report")) {
            return;
        }
        String reason = Objects.requireNonNull(event.getValue("close-reason")).getAsString().trim();

        User user = event.getUser();
        ThreadChannel thread = FeedbackManager.closeReportMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        FeedbackManager.closeReportMap.remove(user.getIdLong());

        event.deferEdit().queue();

        thread.sendMessageEmbeds(new EmbedBuilder()
                .setTitle("リクエスト/報告がクローズされました")
                .setDescription("`%s` のアクションにより、クローズしました。".formatted(user.getName()))
                .addField("理由", reason, false)
                .setFooter("スレッドの管理権限のあるユーザーはメッセージ送信などでスレッドを再開できますが、原則再開させずに新規でリクエスト/報告を立ち上げてください。")
                .setColor(Color.RED)
                .build()).complete();
        
        // タグを解決済みに更新
        updateToResolvedTag(thread);
        
        thread.getManager().setArchived(true).setLocked(true).queue();

        Matcher matcher = FeedbackManager.ISSUE_PATTERN.matcher(thread.getName());
        if (!matcher.find()) {
            return;
        }
        String repository = Main.getConfig().getRepository();
        int issueNumber = Integer.parseInt(matcher.group(1));
        GitHub.createIssueComment(repository,
                issueNumber,
                "`%s` がスレッドをクローズしたため、本 issue もクローズします。\n\n## 理由\n\n%s".formatted(user.getName(), reason));
        GitHub.UpdateIssueResult result = GitHub.updateIssue(repository, issueNumber, GitHub.UpdateType.STATE, "closed");
        if (result.error() != null) {
            Main.getLogger().error("Failed to close issue: " + result.error());
        }
    }

    private void updateToResolvedTag(ThreadChannel thread) {
        Long unresolvedTagId = Main.getConfig().getUnresolvedTagId();
        Long resolvedTagId = Main.getConfig().getResolvedTagId();
        
        if (resolvedTagId == null) {
            return;
        }
        
        ForumChannel forumChannel = (ForumChannel) thread.getParentChannel();
        ForumTag resolvedTag = forumChannel.getAvailableTagById(resolvedTagId);
        
        if (resolvedTag == null) {
            return;
        }
        
        // 現在のタグを取得し、未解決タグを削除して解決済みタグを追加
        List<ForumTag> currentTags = new ArrayList<>(thread.getAppliedTags());
        
        // 未解決タグがあれば削除
        if (unresolvedTagId != null) {
            currentTags.removeIf(tag -> tag.getIdLong() == unresolvedTagId);
        }
        
        // 解決済みタグを追加（重複チェック）
        boolean hasResolvedTag = currentTags.stream().anyMatch(tag -> tag.getIdLong() == resolvedTagId);
        if (!hasResolvedTag) {
            currentTags.add(resolvedTag);
        }
        
        // タグを更新
        thread.getManager().setAppliedTags(currentTags).queue();
    }
}
