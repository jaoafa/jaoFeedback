package com.jaoafa.bugreporter.event;

import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.forums.ForumPost;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;

public class BugMenuSubmitEvent extends ListenerAdapter {
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getModalId().equals("bug-report")) {
            return;
        }
        event.deferReply(true).queue();
        String title = Objects.requireNonNull(event.getValue("title")).getAsString();
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();

        User reporter = event.getUser();
        Message targetMessage = BugManager.messageMap.get(reporter.getIdLong());
        if (targetMessage == null) {
            event.getHook().editOriginal("対象メッセージを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        BugManager.messageMap.remove(reporter.getIdLong());

        BugManager bugManager = Main.getBugManager();
        try {
            ForumPost forum = bugManager.createReport(targetMessage, reporter, title, description);
            event
                    .getHook()
                    .editOriginal("報告に成功しました！以降の対応は %s にて行われますのでご確認ください。".formatted(forum.getThreadChannel().getAsMention()))
                .queue();
        } catch (BugManager.BugReportException e) {
            event
                .getHook()
                .editOriginal("不具合報告に失敗しました: `%s %s`".formatted(e.getClass().getName(), e.getMessage()))
                .queue();
        }
    }
}