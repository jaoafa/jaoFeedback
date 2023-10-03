package com.jaoafa.feedback.lib;

import com.jaoafa.feedback.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class FeedbackModel {
    public static final String BUG_TARGET_REACTION = "\uD83D\uDC1B"; // :bug:
    public static final Modal FeatureRequestModal;
    public static final Modal ImprovementRequestModal;
    public static final Modal BugReportModal;
    public static final ActionRow FeedbackActionRow;
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    static {
        TextInput title = TextInput
                .create("title", "新機能提案のタイトル", TextInputStyle.SHORT)
                .setPlaceholder("新機能について1行で簡潔に説明")
                .setMinLength(3)
                .setMaxLength(70)
                .setRequired(true)
                .build();

        TextInput description = TextInput
                .create("description", "新機能についての説明", TextInputStyle.PARAGRAPH)
                .setPlaceholder("どんな機能が欲しいか、なぜ欲しいかなど")
                .setMinLength(3)
                .setMaxLength(1500)
                .setRequired(true)
                .build();

        FeatureRequestModal = Modal
                .create("feature-request", "新機能リクエスト")
                .addComponents(ActionRow.of(title), ActionRow.of(description))
                .build();
    }

    static {
        TextInput title = TextInput
                .create("title", "機能改善のタイトル", TextInputStyle.SHORT)
                .setPlaceholder("機能改善について1行で簡潔に説明")
                .setMinLength(3)
                .setMaxLength(70)
                .setRequired(true)
                .build();

        TextInput target = TextInput
                .create("target", "対象の機能名", TextInputStyle.SHORT)
                .setPlaceholder("コマンド名、プラグイン名など。分からなければ空白でも可")
                .setMinLength(0)
                .setMaxLength(70)
                .setRequired(false)
                .build();

        TextInput description = TextInput
                .create("description", "改善についての説明", TextInputStyle.PARAGRAPH)
                .setPlaceholder("どんな改善が欲しいか、なぜ欲しいかなど")
                .setMinLength(3)
                .setMaxLength(1500)
                .setRequired(true)
                .build();

        ImprovementRequestModal = Modal
                .create("improvement-request", "機能改善リクエスト")
                .addComponents(ActionRow.of(title), ActionRow.of(target), ActionRow.of(description))
                .build();
    }

    static {
        TextInput title = TextInput
                .create("title", "不具合のタイトル", TextInputStyle.SHORT)
                .setPlaceholder("不具合について1行で簡潔に説明")
                .setMinLength(3)
                .setMaxLength(70)
                .setRequired(true)
                .build();

        TextInput description = TextInput
                .create("description", "不具合についての詳しい説明", TextInputStyle.PARAGRAPH)
                .setPlaceholder("なぜ不具合だと思ったか、改善策など")
                .setMinLength(3)
                .setMaxLength(1500)
                .setRequired(true)
                .build();

        BugReportModal = Modal
                .create("bug-report", "不具合報告")
                .addComponents(ActionRow.of(title), ActionRow.of(description))
                .build();
    }

    static {
        FeedbackActionRow = ActionRow.of(
                Button.primary("change-title", "タイトルの変更")
                        .withEmoji(Emoji.fromUnicode("\uD83D\uDD04")),
                Button.primary("send-to-issue", "Issueにメッセージを送信")
                        .withEmoji(Emoji.fromUnicode("\uD83D\uDCAC")),
                Button.danger("close-report", "リクエスト/報告をクローズ")
                        .withEmoji(Emoji.fromUnicode("\uD83D\uDD10")));
    }

    public static String getMentionContent(User reporter, @Nullable Message message) {
        // 通知ロール + 報告者 + メッセージ送信者
        List<String> mentions = new ArrayList<>();
        mentions.add("<@&959313488113717298>"); // @jDev
        mentions.add(reporter.getAsMention()); // 報告者
        if (message != null && !message.getAuthor().isBot()) {
            // メッセージ送信者がBotではない場合のみメンションする
            mentions.add(message.getAuthor().getAsMention()); // メッセージ送信者
        }

        return String.join(" / ", mentions);
    }

    public static MessageEmbed getFeatureRequestEmbed(
            String description,
            @Nullable Message message,
            User requester,
            GitHub.CreateIssueResult createIssueResult
    ) {
        EmbedBuilder builder = getCommonEmbed(":tools: 新機能リクエスト", Color.GREEN, requester)
                .addField("説明", description, false);
        if (message != null) {
            ZonedDateTime createdAt = message.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Tokyo"));
            builder.addField("対象メッセージ",
                    "%s に送信された %s による %s でのメッセージ\n\n%s".formatted(createdAt.format(FORMATTER),
                            message.getAuthor().getAsMention(),
                            message.getChannel().getAsMention(),
                            message.getJumpUrl()),
                    false);
        }
        builder.addField(getIssueUrlField(createIssueResult));
        return builder.build();
    }

    public static MessageEmbed getImprovementRequestEmbed(
            String description,
            String target,
            @Nullable Message message,
            User requester,
            GitHub.CreateIssueResult createIssueResult
    ) {
        EmbedBuilder builder = getCommonEmbed(":chart_with_upwards_trend: 機能改善リクエスト", Color.YELLOW, requester)
                .addField("対象の機能名", target, false)
                .addField("説明", description, false);
        if (message != null) {
            ZonedDateTime createdAt = message.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Tokyo"));
            builder.addField("対象メッセージ",
                    "%s に送信された %s による %s でのメッセージ\n\n%s".formatted(createdAt.format(FORMATTER),
                            message.getAuthor().getAsMention(),
                            message.getChannel().getAsMention(),
                            message.getJumpUrl()),
                    false);
        }
        builder.addField(getIssueUrlField(createIssueResult));
        return builder.build();
    }

    public static MessageEmbed getBugReportEmbed(
            String description,
            @Nullable Message message,
            User reporter,
            GitHub.CreateIssueResult createIssueResult
    ) {
        EmbedBuilder builder = getCommonEmbed(":bug: 不具合報告", Color.RED, reporter)
                .addField("説明", description, false);
        if (message != null) {
            ZonedDateTime createdAt = message.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Tokyo"));
            String content = message.getContentRaw();
            content = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            content = "> " + content.replaceAll("\n", "\n> ");
            String embedContent = message.getEmbeds().stream()
                    .map(MessageEmbed::getDescription)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            if (!embedContent.isEmpty()) {
                embedContent = embedContent.length() > 100 ? embedContent.substring(0, 100) + "..." : embedContent;
                embedContent = "> " + embedContent.replaceAll("\n", "\n> ");
                content += "\n\n" + embedContent;
            }

            builder.addField("対象メッセージ",
                    "%s に送信された %s による %s でのメッセージ\n\n%s".formatted(createdAt.format(FORMATTER),
                            message.getAuthor().getAsMention(),
                            message.getChannel().getAsMention(),
                            message.getJumpUrl()
                    ),
                    false);
            builder.addField("対象メッセージの内容", content, false);
        }
        builder.addField(getIssueUrlField(createIssueResult));
        return builder.build();
    }

    private static EmbedBuilder getCommonEmbed(String title, Color color, User user) {
        return new EmbedBuilder()
                .setTitle(title)
                .setTimestamp(Instant.now())
                .setColor(color)
                .setAuthor(user.getName(), "https://discord.com/users/" + user.getId(), user.getAvatarUrl());
    }

    private static MessageEmbed.Field getIssueUrlField(GitHub.CreateIssueResult createIssueResult) {
        String repository = Main.getConfig().getRepository();
        return new MessageEmbed.Field("Issue URL", "https://github.com/%s/issues/%d".formatted(repository, createIssueResult.issueNumber()), false);
    }

    public static String getFeatureRequestIssueBody(
            @NotNull String description,
            @Nullable Message message,
            @NotNull User requester
    ) {
        return """
                # 新機能リクエスト
                                
                %s
                                
                ## 対象メッセージ
                            
                %s
                            
                ## リクエストしたユーザー
                            
                `%s` (`%s`)
                """
                .formatted(
                        description,
                        getMessageText(message),
                        requester.getName(),
                        requester.getId())
                .trim();
    }

    public static String getImprovementRequestIssueBody(
            @NotNull String description,
            @NotNull String target,
            @Nullable Message message,
            @NotNull User requester
    ) {
        return """
                # 機能改善リクエスト
                                
                %s
                                
                ## 対象の機能名
                                
                %s
                                
                ## 対象メッセージ
                            
                %s
                            
                ## リクエストしたユーザー
                            
                `%s` (`%s`)
                """
                .formatted(
                        description,
                        target,
                        getMessageText(message),
                        requester.getName(),
                        requester.getId())
                .trim();
    }

    public static String getBugReportIssueBody(
            @NotNull String description,
            @Nullable Message message,
            @NotNull User reporter
    ) {
        return """
                # 不具合報告
                                
                %s
                                
                ## 不具合と思われるメッセージ (または報告)
                            
                %s
                            
                ## 不具合報告者
                            
                `%s` (`%s`)
                """
                .formatted(
                        description,
                        getMessageText(message),
                        reporter.getName(),
                        reporter.getId())
                .trim();
    }

    private static String getMessageText(Message message) {
        return message != null ?
                "%s に送信された `%s` による `#%s` でのメッセージ\n\nメッセージURL: %s".formatted(
                        message.getTimeCreated().atZoneSameInstant(ZoneId.of("Asia/Tokyo")).format(FORMATTER),
                        message.getAuthor().getName(),
                        message.getChannel().getName(),
                        message.getJumpUrl()) : "NULL";
    }
}
