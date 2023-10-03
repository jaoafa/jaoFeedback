package com.jaoafa.feedback.event;

import com.jaoafa.feedback.Main;
import com.jaoafa.feedback.lib.FeedbackManager;
import com.jaoafa.feedback.lib.GitHub;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;

public class ChangeTitleSubmitEvent extends ListenerAdapter {
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (event.getGuild() == null || event.getGuild().getIdLong() != Main.getConfig().getGuildId()) {
            event.reply("このサーバでは利用できません。").setEphemeral(true).queue();
            return;
        }
        if (!event.getModalId().equals("change-title")) {
            return;
        }
        String newTitle = Objects.requireNonNull(event.getValue("new-title")).getAsString();

        User user = event.getUser();
        ThreadChannel thread = FeedbackManager.changeTitleMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        FeedbackManager.changeTitleMap.remove(user.getIdLong());

        event.deferEdit().queue();

        String threadTitle;
        int issueNumber = -1;
        String prevTitle = thread.getName();
        Matcher matcher = FeedbackManager.ISSUE_PATTERN.matcher(prevTitle);
        if (matcher.find()) {
            issueNumber = Integer.parseInt(matcher.group(1));
            threadTitle = "*%d %s".formatted(issueNumber, newTitle);
        } else {
            threadTitle = newTitle;
        }

        thread.sendMessage("`%s` のアクションにより、タイトルを変更します。".formatted(user.getName())).complete();
        thread.getManager().setName(threadTitle).queue();

        if (issueNumber == -1) {
            return;
        }
        String repository = Main.getConfig().getRepository();
        GitHub.UpdateIssueResult result = GitHub.updateIssue(repository, issueNumber, GitHub.UpdateType.TITLE, newTitle);
        if (result.error() != null) {
            thread.sendMessage("GitHubへのタイトルの変更に失敗しました: ```%s```".formatted(result.error())).complete();
        }
    }
}
