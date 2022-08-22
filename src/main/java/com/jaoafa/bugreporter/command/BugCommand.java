package com.jaoafa.bugreporter.command;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.Objects;

public class BugCommand extends SlashCommand {
    public BugCommand() {
        this.name = "bug";
        this.help = "特定のメッセージに対する不具合報告を行います。";
        this.options = List.of(new OptionData(OptionType.STRING, "message_id", "不具合報告をしたいメッセージのメッセージID", true));
        this.userPermissions = new Permission[]{Permission.VIEW_CHANNEL};
    }

    @Override
    public void execute(SlashCommandEvent event) {
        MessageChannel channel = event.getMessageChannel();
        String messageId = Objects.requireNonNull(event.getOption("message_id")).getAsString();

        Message message;
        try {
            message = channel.retrieveMessageById(messageId).complete();
        } catch (Exception e) {
            event.reply("指定されたメッセージIDのメッセージが見つかりませんでした。").setEphemeral(true).queue();
            return;
        }

        List<User> users = message.retrieveReactionUsers(Emoji.fromUnicode(BugManager.TARGET_REACTION)).complete();
        if (users.size() > 0) {
            // 1人以上 = 既に報告済み
            event.reply("このメッセージはすでに報告済みです。").setEphemeral(true).queue();
            return;
        }

        BugManager bugManager = Main.getBugManager();
        if (bugManager.isReported(message)) {
            event.reply("このメッセージはすでに報告済みです。").setEphemeral(true).queue();
            return;
        }

        BugManager.messageMap.put(event.getUser().getIdLong(), message);

        event.replyModal(BugManager.getModal()).queue();
    }
}
