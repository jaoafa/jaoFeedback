package com.jaoafa.bugreporter.lib;

import com.jaoafa.bugreporter.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class BugManager {
    public static Map<Long, Message> messageMap = new HashMap<>();
    public static Map<Long, ThreadChannel> changeTitleMap = new HashMap<>();
    public static Map<Long, ThreadChannel> sendToIssueMap = new HashMap<>();
    public static Map<Long, ThreadChannel> closeReportMap = new HashMap<>();
    public static final Pattern ISSUE_PATTERN = Pattern.compile("^\\*(\\d+) ");
    public static final String TARGET_REACTION = "\uD83D\uDC1B"; // :bug:
    public static final String REPOSITORY = "jaoafa/jao-Minecraft-Server";
    private final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private final Path REPORTS_PATH;

    public BugManager() {
        REPORTS_PATH = System.getenv("REPORTS_PATH") != null ?
                Path.of(System.getenv("REPORTS_PATH")) :
                Path.of("reports.json");
    }

    public static Modal getModal() {
        TextInput title = TextInput
                .create("title", "タイトル", TextInputStyle.SHORT)
                .setPlaceholder("不具合についてのタイトル（簡単な説明）")
                .setMinLength(3)
                .setMaxLength(70)
                .setRequired(true)
                .build();

        TextInput description = TextInput
                .create("description", "説明", TextInputStyle.PARAGRAPH)
                .setPlaceholder("不具合についての詳しい説明（なぜ不具合だと思ったか、改善策など）")
                .setMinLength(3)
                .setMaxLength(1500)
                .setRequired(true)
                .build();

        return Modal
                .create("bug-report", "不具合報告")
                .addActionRows(ActionRow.of(title), ActionRow.of(description))
                .build();
    }

    public boolean isReported(Message message) {
        return getReport(message) != null;
    }

    public BugReport getReport(Message message) {
        List<BugReport> reports = loadReports();
        if (reports == null) {
            return null;
        }
        return reports.stream().filter(r -> r.messageId == message.getIdLong()).findAny().orElse(null);
    }

    public ThreadChannel createReport(Message message,
                                      User reporter,
                                      String title,
                                      @Nullable String description) throws BugReportException {
        if (title == null) {
            title = "%s による #%s での不具合報告".formatted(reporter.getAsTag(), message.getChannel().getName());
        }

        // Issueを作成
        String githubBody = generateGitHubBody(message, reporter, description);

        GitHub.CreateIssueResult createIssueResult = GitHub.createIssue(REPOSITORY, title, githubBody);

        // スレッドを作成
        JDA jda = Main.getJDA();
        Config config = Main.getConfig();
        TextChannel channel = jda.getTextChannelById(config.getChannelId());
        if (channel == null) {
            throw new BugReportException("スレッド用のチャンネルを見つけられませんでした。");
        }

        String threadTitle = (createIssueResult.error() == null ?
                "*" + createIssueResult.issueNumber() + " " :
                "") + title;
        ThreadChannel thread = createThread(threadTitle);
        thread.sendMessage(generateThreadStartMessage(message, createIssueResult, reporter, description)).queue();
        saveReport(new BugReport(message.getIdLong(),
                new BugUser(reporter.getIdLong(), reporter.getName(), reporter.getDiscriminator()),
                thread.getIdLong(),
                createIssueResult.issueNumber()));
        return thread;
    }

    private MessageCreateData generateThreadStartMessage(Message message,
                                                         GitHub.CreateIssueResult createIssueResult,
                                                         User reporter,
                                                         String description) {
        List<String> messages = new ArrayList<>();
        if (createIssueResult.error() == null) {
            // Issueが作成できた場合はリンクさせる
            messages.add("[LINKED-ISSUE:%s#%d]".formatted(REPOSITORY, createIssueResult.issueNumber()));
            messages.add("");
        }
        // 通知ロール + 報告者 + メッセージ送信者
        List<String> mentions = new ArrayList<>();
        mentions.add("<@&959313488113717298>"); // @jDev
        mentions.add(reporter.getAsMention()); // 報告者
        if (!message.getAuthor().isBot()) {
            // メッセージ送信者がBotではない場合のみメンションする
            mentions.add(message.getAuthor().getAsMention()); // メッセージ送信者
        }

        messages.add(String.join(" / ", mentions));

        ZonedDateTime createdAt = message.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(":bug: 不具合報告")
                .setTimestamp(Instant.now())
                .setColor(Color.YELLOW);
        if (description == null) {
            builder.setFooter("投稿者、または報告者は不具合内容についての説明（何が不具合と思ったのか、期待される動作など）をお願いします。");
        } else {
            builder.addField("説明", description, false);
        }
        builder
                .addField("対象メッセージ",
                        "%s に送信された %s による %s でのメッセージ\n\n%s".formatted(createdAt.format(FORMATTER),
                                message.getAuthor().getAsMention(),
                                message.getChannel().getAsMention(),
                                message.getJumpUrl()),
                        false)
                .addField("不具合報告者", reporter.getAsMention(), false)
                .addField("Issue URL",
                        "https://github.com/%s/issues/%d".formatted(REPOSITORY, createIssueResult.issueNumber()), false);

        return new MessageCreateBuilder()
                .setContent(String.join("\n", messages))
                .setEmbeds(builder.build())
                .addActionRow(Button.primary("change-title", "報告タイトルの変更").withEmoji(Emoji.fromUnicode("\uD83D\uDD04")),
                        Button.primary("send-to-issue", "Issueにメッセージを送信").withEmoji(Emoji.fromUnicode("\uD83D\uDCAC")),
                        Button.danger("close-report", "報告をクローズ").withEmoji(Emoji.fromUnicode("\uD83D\uDD10")))
                .build();
    }

    private List<BugReport> loadReports() {
        try {
            List<BugReport> reports = new ArrayList<>();
            if (Files.exists(REPORTS_PATH)) {
                JSONArray array = new JSONArray(Files.readString(REPORTS_PATH));
                for (int i = 0; i < array.length(); i++) {
                    reports.add(BugReport.fromJSON(array.getJSONObject(i)));
                }
            }
            return reports;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveReport(BugReport report) {
        try {
            JSONArray array = new JSONArray();
            if (Files.exists(REPORTS_PATH)) {
                array = new JSONArray(Files.readString(REPORTS_PATH));
            }
            array.put(report.toJSON());
            Files.writeString(REPORTS_PATH, array.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String generateGitHubBody(@Nonnull Message message,
                                      @Nonnull User reporter,
                                      @Nullable String userDescription) {
        ZonedDateTime createdAt = message.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Tokyo"));
        return """
                %s
                                
                ## 不具合と思われるメッセージ (または報告)
                            
                %s に送信された `%s` による `#%s` でのメッセージ
                            
                メッセージ URL: %s
                            
                ## 不具合報告者
                            
                `%s` (`%s`)
                """
                .formatted(userDescription,
                        createdAt.format(FORMATTER),
                        message.getAuthor().getAsTag(),
                        message.getChannel().getName(),
                        message.getJumpUrl(),
                        reporter.getAsTag(),
                        reporter.getId())
                .trim();
    }

    private ThreadChannel createThread(String threadName) {
        long channelId = Main.getConfig().getChannelId();
        JDA jda = Main.getJDA();

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new NullPointerException("Get channel failed");
        }
        return channel.createThreadChannel(threadName).complete();
    }

    public record BugReport(long messageId, BugUser reporter, long threadId, int issueNumber) {
        JSONObject toJSON() {
            return new JSONObject()
                    .put("messageId", messageId)
                    .put("reporter", reporter.toJSON())
                    .put("threadId", threadId)
                    .put("issueNumber", issueNumber);
        }

        static BugReport fromJSON(JSONObject json) {
            return new BugReport(json.getLong("messageId"),
                    BugUser.fromJSON(json.getJSONObject("reporter")),
                    json.getLong("threadId"),
                    json.getInt("issueNumber"));
        }
    }

    record BugUser(long userId, String username, String discriminator) {
        JSONObject toJSON() {
            return new JSONObject().put("userId", userId).put("username", username).put("discriminator", discriminator);
        }

        static BugUser fromJSON(JSONObject json) {
            return new BugUser(json.getLong("userId"), json.getString("username"), json.getString("discriminator"));
        }
    }

    public static class BugReportException extends Exception {
        BugReportException(String message) {
            super(message);
        }
    }
}
