package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
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
        event.deferReply(false).queue();
        String title = Objects.requireNonNull(event.getValue("title")).getAsString();
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();

        User reporter = event.getUser();
        Message targetMessage = FeedbackManager.messageMap.get(reporter.getIdLong());
        if (targetMessage == null) {
            event.getHook().editOriginal("対象メッセージを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        FeedbackManager.messageMap.remove(reporter.getIdLong());

        FeedbackManager feedbackManager = Main.getFeedbackManager();
        try {
            ForumPost forum = feedbackManager.createBugReport(targetMessage, reporter, title, description);
            event.getHook().editOriginal("報告に成功しました！以降の対応は %s にて行われますのでご確認ください。".formatted(forum.getThreadChannel().getAsMention())).queue();
        } catch (FeedbackManager.FeedbackException e) {
            event.getHook().editOriginal("不具合報告に失敗しました: `%s %s`".formatted(e.getClass().getName(), e.getMessage())).queue();
        }
    }
}