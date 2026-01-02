package com.jaoafa.feedback;

import com.jaoafa.feedback.event.*;
import com.jaoafa.feedback.lib.Config;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.menu.BugMenu;
import com.jaoafa.feedback.menu.FeatureMenu;
import com.jaoafa.feedback.menu.ImprovementMenu;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Main {
    static final Logger logger = LoggerFactory.getLogger("jaoFeedback");

    static JDA jda;
    static Config config;
    static FeedbackManager feedbackManager;
    static ScheduledExecutorService scheduler;

    public static void main(String[] args) {
        logger.info("Starting jaoFeedback...");

        config = new Config();

        try {
            jda = JDABuilder
                    .createDefault(config.getToken())
                    .enableIntents(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS)
                    .setAutoReconnect(true)
                    .setBulkDeleteSplittingEnabled(false)
                    .setContextEnabled(false)
                    .addEventListeners(
                            new BugMenu(),
                            new FeatureMenu(),
                            new ImprovementMenu(),
                            new DiscordReadyEvent(),
                            new BugReactionEvent(),
                            new BugMenuSubmitEvent(),
                            new FeatureMenuSubmitEvent(),
                            new ImprovementMenuSubmitEvent(),
                            new ChangeTitleSubmitEvent(),
                            new SendToIssueEvent(),
                            new TransferIssueEvent(),
                            new TransferIssueSelectEvent(),
                            new CloseReportEvent(),
                            new ThreadButtonEvent())
                    .build()
                    .awaitReady();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        feedbackManager = new FeedbackManager();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        logger.info("Started jaoFeedback.");
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

    public static FeedbackManager getFeedbackManager() {
        return feedbackManager;
    }

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
