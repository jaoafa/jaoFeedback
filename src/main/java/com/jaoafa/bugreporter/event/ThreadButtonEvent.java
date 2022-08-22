package com.jaoafa.bugreporter.event;

import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import com.jaoafa.bugreporter.lib.Config;
import com.jaoafa.bugreporter.lib.GitHub;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ThreadChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.internal.utils.PermissionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;

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
            .create("new-title", "新しいタイトル", TextInputStyle.SHORT)
            .setPlaceholder("新しいタイトル")
            .setMinLength(3)
            .setMaxLength(70)
            .setRequired(true)
            .build();

        BugManager.changeTitleMap.put(event.getUser().getIdLong(), thread);

        event
            .replyModal(Modal.create("change-title", "報告タイトルの変更").addActionRows(ActionRow.of(newTitle)).build())
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
        event.deferEdit().queue();

        User user = event.getUser();
        thread.sendMessage("`%s` のアクションにより、報告をクローズします".formatted(user.getAsTag())).complete();
        thread.getManager().setArchived(true).setLocked(true).queue();

        Matcher matcher = BugManager.ISSUE_PATTERN.matcher(thread.getName());
        if (!matcher.find()) {
            return;
        }
        int issueNumber = Integer.parseInt(matcher.group(1));
        GitHub.createIssueComment(BugManager.REPOSITORY,
                                  issueNumber,
                                  "`%s` がスレッドをクローズしたため、本 issue もクローズします。".formatted(user.getAsTag()));
        GitHub.updateIssue(BugManager.REPOSITORY, issueNumber, GitHub.UpdateType.STATE, "closed");
    }
}
