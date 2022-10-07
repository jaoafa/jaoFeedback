package com.jaoafa.bugreporter;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jaoafa.bugreporter.command.BugCommand;
import com.jaoafa.bugreporter.event.*;
import com.jaoafa.bugreporter.lib.BugManager;
import com.jaoafa.bugreporter.lib.Config;
import com.jaoafa.bugreporter.menu.BugMenu;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
    static final Logger logger = LoggerFactory.getLogger("Javajaotan2");

    static JDA jda;
    static Config config;
    static BugManager bugManager;
    static ScheduledExecutorService scheduler;

    public static void main(String[] args) {
        logger.info("Starting BugReporter...");

        config = new Config();
        CommandClient commandClient = new CommandClientBuilder()
            .setActivity(null)
            .setOwnerId(config.getOwnerId())
            .addSlashCommand(new BugCommand())
            .addContextMenu(new BugMenu())
            .forceGuildOnly(config.getGuildId())
            .build();

        try {
            jda = JDABuilder
                .createDefault(config.getToken())
                .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                .setAutoReconnect(true)
                .setBulkDeleteSplittingEnabled(false)
                .setContextEnabled(false)
                .addEventListeners(new EventWaiter(),
                        commandClient,
                        new DiscordReadyEvent(),
                        new BugReactionEvent(),
                        new BugMenuSubmitEvent(),
                        new ChangeTitleSubmitEvent(),
                        new SendToIssueEvent(),
                        new CloseReportEvent(),
                        new ThreadButtonEvent())
                .build()
                .awaitReady();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        bugManager = new BugManager();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        logger.info("Started BugReporter.");
    }

    public static Logger getLogger() {
        return logger;
    }

    public static JDA getJDA() {
        return jda;
    }

    public static Config getConfig() {
        return config;
    }

    public static BugManager getBugManager() {
        return bugManager;
    }

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
