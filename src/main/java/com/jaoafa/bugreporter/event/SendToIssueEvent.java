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

public class SendToIssueEvent extends ListenerAdapter {
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getModalId().equals("send-to-issue")) {
            return;
        }
        String content = Objects.requireNonNull(event.getValue("content")).getAsString();

        User user = event.getUser();
        ThreadChannel thread = BugManager.sendToIssueMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        BugManager.sendToIssueMap.remove(user.getIdLong());

        int issueNumber = -1;
        String prevTitle = thread.getName();
        Matcher matcher = BugManager.ISSUE_PATTERN.matcher(prevTitle);
        if (matcher.find()) {
            issueNumber = Integer.parseInt(matcher.group(1));
        }

        if (issueNumber == -1) {
            return;
        }
        String repository = Main.getConfig().getRepository();
        GitHub.CreateIssueCommentResult result = GitHub.createIssueComment(repository, issueNumber, "%s\n\n`%s` によるメッセージ".formatted(content, user.getAsTag()));

        if (result.error() != null) {
            event.reply("GitHubへのメッセージの送信に失敗しました: ```%s```".formatted(result.error())).setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue();

        thread.sendMessageEmbeds(
                new EmbedBuilder()
                        .setDescription(content)
                        .addField("URL", result.htmlUrl(), false)
                        .setAuthor(user.getName(), "https://discord.com/users/%s".formatted(user.getId()), user.getAvatarUrl())
                        .setColor(Color.GREEN)
                        .setFooter("GitHub Issue にメッセージを送信")
                        .build()
        ).queue();
    }
}
