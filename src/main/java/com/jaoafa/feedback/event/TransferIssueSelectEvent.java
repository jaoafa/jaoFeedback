package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

public class TransferIssueSelectEvent extends ListenerAdapter {
    private static final String MANUAL_VALUE = "__manual__";

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getComponentId().equals("transfer-issue-select")) {
            return;
        }
        if (event.getValues().isEmpty()) {
            event.reply("移動先リポジトリを選択してください。").setEphemeral(true).queue();
            return;
        }

        User user = event.getUser();
        ThreadChannel thread = FeedbackManager.transferIssueMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }

        String selected = event.getValues().get(0);
        if (MANUAL_VALUE.equals(selected)) {
            TextInput repositoryRow = TextInput
                    .create("repository", TextInputStyle.SHORT)
                    .setPlaceholder("移動先リポジトリ (repo または owner/repo)")
                    .setMinLength(3)
                    .setMaxLength(200)
                    .setRequired(true)
                    .build();

            event.replyModal(Modal.create("transfer-issue", "Issueを移動")
                            .addComponents(Label.of("移動先リポジトリ", repositoryRow))
                            .build())
                    .queue();
            return;
        }

        FeedbackManager.transferIssueMap.remove(user.getIdLong());

        TransferIssueEvent.TransferOutcome outcome = TransferIssueEvent.transferIssue(user, thread, selected);
        if (!outcome.success()) {
            event.reply(outcome.errorMessage()).setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
    }
}
