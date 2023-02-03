package com.jaoafa.feedback.menu;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.lib.FeedbackModel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

public class BugMenu extends ListenerAdapter {
    public static void register(JDA jda) {
        Guild guild = jda.getGuildById(Main.getConfig().getGuildId());
        if (guild == null) {
            return;
        }

        guild.upsertCommand(Commands.context(Command.Type.MESSAGE, "不具合報告")).queue();
    }

    @Override
    public void onMessageContextInteraction(MessageContextInteractionEvent event) {
        if (!event.getName().equals("不具合報告")) {
            return;
        }
        Message message = event.getTarget();
        List<User> users = message.retrieveReactionUsers(Emoji.fromUnicode(FeedbackModel.BUG_TARGET_REACTION)).complete();
        if (users.size() > 0) {
            // 1人以上 = 既に報告済み
            event.reply("このメッセージはすでに報告済みです。").setEphemeral(true).queue();
            return;
        }

        FeedbackManager feedbackManager = new FeedbackManager();
        if (feedbackManager.isAlreadyFeedback(message)) {
            event.reply("このメッセージはすでに報告済みです。").setEphemeral(true).queue();
            return;
        }

        FeedbackManager.messageMap.put(event.getUser().getIdLong(), message);

        event.replyModal(FeedbackModel.BugReportModal).queue();
    }
}
