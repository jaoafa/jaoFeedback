package com.jaoafa.bugreporter.event;

import com.jaoafa.bugreporter.Main;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class DiscordReadyEvent extends ListenerAdapter {
    @Override
    public void onReady(ReadyEvent event) {
        JDA jda = event.getJDA();
        Main.getLogger().info("Ready: " + jda.getSelfUser().getAsTag());
    }
}
