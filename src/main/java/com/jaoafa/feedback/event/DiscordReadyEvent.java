package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.menu.BugMenu;
import com.jaoafa.feedback.menu.FeatureMenu;
import com.jaoafa.feedback.menu.ImprovementMenu;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

public class DiscordReadyEvent extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        Main.getLogger().info("Ready: " + jda.getSelfUser().getName());

        // bugコマンドは削除する
        for (Guild guild : jda.getGuilds()) {
            guild.retrieveCommands().queue(commands -> {
                for (Command command : commands) {
                    if (command.getName().equals("bug")) {
                        command.delete().queue();
                    }
                }
            });
        }

        // メニューなどの登録
        BugMenu.register(jda);
        FeatureMenu.register(jda);
        ImprovementMenu.register(jda);
    }
}
