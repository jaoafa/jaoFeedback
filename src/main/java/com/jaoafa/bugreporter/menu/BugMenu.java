package com.jaoafa.bugreporter.menu;

import com.jagrosh.jdautilities.command.MessageContextMenu;
import com.jagrosh.jdautilities.command.MessageContextMenuEvent;
import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.List;

public class BugMenu extends MessageContextMenu {

    public BugMenu() {
        this.name = "不具合報告";
        this.guildOnly = true;
        this.userPermissions = new Permission[]{Permission.VIEW_CHANNEL};
    }

    @Override
    protected void execute(MessageContextMenuEvent event) {
        Message message = event.getTarget();
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
