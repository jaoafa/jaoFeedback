package com.jaoafa.bugreporter.event;

import com.jaoafa.bugreporter.Main;
import com.jaoafa.bugreporter.lib.BugManager;
import com.jaoafa.bugreporter.lib.GitHub;
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
        ThreadChannel thread = BugManager.changeTitleMap.get(user.getIdLong());
        if (thread == null) {
            event.reply("対象スレッドを見つけられませんでした。もう一度お試しください。").queue();
            return;
        }
        BugManager.changeTitleMap.remove(user.getIdLong());

        event.deferEdit().queue();

        String threadTitle;
        int issueNumber = -1;
        String prevTitle = thread.getName();
        Matcher matcher = BugManager.ISSUE_PATTERN.matcher(prevTitle);
        if (matcher.find()) {
            issueNumber = Integer.parseInt(matcher.group(1));
            threadTitle = "*%d %s".formatted(issueNumber, newTitle);
        } else {
            threadTitle = newTitle;
        }

        thread.sendMessage("`%s` のアクションにより、タイトルを変更します。".formatted(user.getAsTag())).complete();
        thread.getManager().setName(threadTitle).queue();

        if (issueNumber == -1) {
            return;
        }
        GitHub.updateIssue(BugManager.REPOSITORY, issueNumber, GitHub.UpdateType.TITLE, newTitle);
    }
}
