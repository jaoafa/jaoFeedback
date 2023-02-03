package com.jaoafa.feedback.menu;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.lib.FeedbackModel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class ImprovementMenu extends ListenerAdapter {
    public static void register(JDA jda) {
        Guild guild = jda.getGuildById(Main.getConfig().getGuildId());
        if (guild == null) {
            return;
        }

        guild.upsertCommand(Commands.context(Command.Type.MESSAGE, "機能改善リクエスト")).queue();
        guild.upsertCommand(Commands.context(Command.Type.USER, "機能改善リクエスト")).queue();
    }

    @Override
    public void onUserContextInteraction(UserContextInteractionEvent event) {
        if (!event.getName().equals("機能改善リクエスト")) {
            return;
        }

        event.replyModal(FeedbackModel.ImprovementRequestModal).queue();
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!event.getName().equals("機能改善リクエスト")) {
            return;
        }
        Message message = event.getTarget();

        FeedbackManager feedbackManager = new FeedbackManager();
        if (feedbackManager.isAlreadyFeedback(message)) {
            event.reply("このメッセージはすでにリクエスト済みです。").setEphemeral(true).queue();
            return;
        }

        FeedbackManager.messageMap.put(event.getUser().getIdLong(), message);

        event.replyModal(FeedbackModel.ImprovementRequestModal).queue();
    }
}
