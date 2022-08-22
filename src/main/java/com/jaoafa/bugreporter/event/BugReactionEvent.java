package com.jaoafa.bugreporter.event;

import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class BugReactionEvent extends ListenerAdapter {
    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (Main.getConfig().getGuildId() != event.getGuild().getIdLong()) {
            return;
        }

        User user = event.getUser();

        if (user == null) {
            user = event.retrieveUser().complete();
        }
        if (user.isBot()) {
            return;
        }

        EmojiUnion emoji = event.getEmoji();

        if (emoji.getType() != Emoji.Type.UNICODE) {
            return;
        }
        String targetReaction = BugManager.TARGET_REACTION;
        if (!emoji.asUnicode().getName().equals(targetReaction)) {
            return;
        }

        Message message = event.retrieveMessage().complete();
        List<User> users = message.retrieveReactionUsers(Emoji.fromUnicode(targetReaction)).complete();

        if (users.size() != 1) {
            // 1人以外 = 0もしくは2人以上 = 既に報告済み
            return;
        }
        MessageChannelUnion channel = event.getChannel();

        BugManager bugManager = Main.getBugManager();

        if (bugManager.isReported(message)) {
            channel
                .sendMessage("%s, この不具合はすでに報告済みです。".formatted(user.getAsMention()))
                .delay(1, TimeUnit.MINUTES, Main.getScheduler()) // delete 1 minute later
                .flatMap(Message::delete)
                .queue();
            return;
        }

        try {
            bugManager.createReport(message, user, null, null);
        } catch (BugManager.BugReportException e) {
            channel
                .sendMessage("%s, 不具合報告に失敗しました: `%s %s`".formatted(user.getAsMention(),
                                                                   e.getClass().getName(),
                                                                   e.getMessage()))
                .delay(1, TimeUnit.MINUTES, Main.getScheduler()) // delete 1 minute later
                .flatMap(Message::delete)
                .queue();
        }

        message.addReaction(Emoji.fromUnicode(targetReaction)).queue();
    }
}

