package com.jaoafa.bugreporter.event;

import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import com.jaoafa.bugreporter.lib.GitHub;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
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
        ThreadChannel thread = BugManager.closeReportMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        BugManager.closeReportMap.remove(user.getIdLong());

        event.deferEdit().queue();

        thread.sendMessageEmbeds(new EmbedBuilder()
                .setTitle("報告がクローズされました")
                .setDescription("`%s` のアクションにより、報告をクローズしました。".formatted(user.getAsTag()))
                .addField("理由", reason, false)
                .setFooter("スレッドの管理権限のあるユーザーはメッセージ送信などでスレッドを再開できますが、原則再開させずに新規で報告を立ち上げてください。")
                .setColor(Color.RED)
                .build()).complete();
        thread.getManager().setArchived(true).setLocked(true).queue();

        Matcher matcher = BugManager.ISSUE_PATTERN.matcher(thread.getName());
        if (!matcher.find()) {
            return;
        }
        int issueNumber = Integer.parseInt(matcher.group(1));
        GitHub.createIssueComment(BugManager.REPOSITORY,
                issueNumber,
                "`%s` がスレッドをクローズしたため、本 issue もクローズします。\n\n## 理由\n\n%s".formatted(user.getAsTag(), reason));
        GitHub.updateIssue(BugManager.REPOSITORY, issueNumber, GitHub.UpdateType.STATE, "closed");

    }
}
