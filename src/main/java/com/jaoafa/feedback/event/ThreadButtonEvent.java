package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.Config;
import com.jaoafa.feedback.lib.FeedbackManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ThreadButtonEvent extends ListenerAdapter {

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        Config config = Main.getConfig();
        if (event.getGuild() == null || event.getMember() == null || event
                .getGuild()
                .getIdLong() != config.getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        MessageChannelUnion channel = event.getChannel();
        if (!channel.getType().isThread()) {
            event.reply("このチャンネルでは利用できません。").setEphemeral(true).queue();
            return;
        }
        ThreadChannel thread = channel.asThreadChannel();
        if (thread.getParentChannel().getIdLong() != config.getChannelId() || !thread.isOwner()) {
            event.reply("このスレッドでは利用できません。").setEphemeral(true).queue();
            return;
        }

        if (event.getComponentId().equals("change-title")) {
            // タイトルの変更
            actionChangeTitle(event, thread);
        }
        if (event.getComponentId().equals("send-to-issue")) {
            // GitHub Issueにメッセージを送信
            actionSendToIssue(event, thread);
        }
        if (event.getComponentId().equals("close-report")) {
            // Reportをクローズ
            actionCloseReport(event, thread);
        }
    }

    void actionChangeTitle(ButtonInteractionEvent event, ThreadChannel thread) {
        if (thread.isLocked()) {
            event.reply("このスレッドはすでにロックされています。").setEphemeral(true).queue();
            return;
        }
        if (!PermissionUtil.checkPermission(thread.getPermissionContainer(),
                Objects.requireNonNull(event.getMember()),
                Permission.MANAGE_THREADS)) {
            event.reply("あなたにはこのアクションを実行する権限がありません。").setEphemeral(true).queue();
            return;
        }
        TextInput newTitle = TextInput
                .create("new-title", TextInputStyle.SHORT)
                .setPlaceholder("新しいタイトル")
                .setMinLength(3)
                .setMaxLength(70)
                .setRequired(true)
                .build();

        FeedbackManager.changeTitleMap.put(event.getUser().getIdLong(), thread);

        event.replyModal(Modal.create("change-title", "タイトルの変更")
                        .addComponents(Label.of("新しいタイトル", newTitle))
                        .build())
                .queue();
    }

    void actionSendToIssue(ButtonInteractionEvent event, ThreadChannel thread) {
        if (thread.isLocked()) {
            event.reply("このスレッドはすでにロックされています。").setEphemeral(true).queue();
            return;
        }
        if (!PermissionUtil.checkPermission(thread.getPermissionContainer(),
                Objects.requireNonNull(event.getMember()),
                Permission.MESSAGE_SEND_IN_THREADS)) {
            event.reply("あなたにはこのアクションを実行する権限がありません。").setEphemeral(true).queue();
            return;
        }

        TextInput messageRow = TextInput
                .create("content", TextInputStyle.PARAGRAPH)
                .setPlaceholder("送信するメッセージのコンテンツを入力")
                .setMinLength(1)
                .setMaxLength(2000)
                .setRequired(true)
                .build();

        FeedbackManager.sendToIssueMap.put(event.getUser().getIdLong(), thread);

        event.replyModal(Modal.create("send-to-issue", "Issueにメッセージを送信")
                        .addComponents(Label.of("メッセージ", messageRow))
                        .build())
                .queue();
    }

    void actionCloseReport(ButtonInteractionEvent event, ThreadChannel thread) {
        if (thread.isLocked()) {
            event.reply("このスレッドはすでにロックされています。").setEphemeral(true).queue();
            return;
        }
        if (!PermissionUtil.checkPermission(thread.getPermissionContainer(),
                Objects.requireNonNull(event.getMember()),
                Permission.MANAGE_THREADS)) {
            event.reply("あなたにはこのアクションを実行する権限がありません。").setEphemeral(true).queue();
            return;
        }

        TextInput messageRow = TextInput
                .create("close-reason", TextInputStyle.PARAGRAPH)
                .setPlaceholder("リクエスト/報告を閉じる理由を入力してください（XXXXXで修正した・対応の必要がない など）")
                .setMinLength(1)
                .setMaxLength(2000)
                .setRequired(true)
                .build();

        FeedbackManager.closeReportMap.put(event.getUser().getIdLong(), thread);

        event.replyModal(Modal.create("close-report", "リクエスト/報告をクローズ")
                        .addComponents(Label.of("リクエスト/報告を閉じる理由", messageRow))
                        .build())
                .queue();
    }
}
