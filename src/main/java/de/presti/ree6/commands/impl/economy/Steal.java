package de.presti.ree6.commands.impl.economy;

import de.presti.ree6.commands.Category;
import de.presti.ree6.commands.CommandEvent;
import de.presti.ree6.commands.interfaces.Command;
import de.presti.ree6.commands.interfaces.ICommand;
import de.presti.ree6.sql.SQLSession;
import de.presti.ree6.utils.data.EconomyUtil;
import de.presti.ree6.utils.others.RandomUtils;
import de.presti.ree6.utils.others.ThreadUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.internal.interactions.CommandDataImpl;

import java.time.Duration;
import java.util.ArrayList;

/**
 * Steal money from other users.
 */
@Command(name = "steal", description = "command.description.steal", category = Category.ECONOMY)
public class Steal implements ICommand {

    /**
     * List of every User that is on cooldown.
     */
    ArrayList<String> stealTimeout = new ArrayList<>();

    /**
     * @inheritDoc
     */
    @Override
    public void onPerform(CommandEvent commandEvent) {
        if (!commandEvent.isSlashCommand()) {
            commandEvent.reply(commandEvent.getResource("command.perform.onlySlashSupported"));
            return;
        }

        String entryString = commandEvent.getGuild().getIdLong() + "-" + commandEvent.getMember().getIdLong();

        SQLSession.getSqlConnector().getSqlWorker().getSetting(commandEvent.getGuild().getIdLong(),
                "configuration_steal_delay").subscribe(value -> {
            long delay = Long.parseLong(value.get().getStringValue());

            if (stealTimeout.contains(entryString)) {
                commandEvent.reply(commandEvent.getResource("message.steal.cooldown", delay));
                return;
            }

            OptionMapping user = commandEvent.getOption("user");

            if (user == null) {
                commandEvent.reply(commandEvent.getResource("message.default.invalidOption"), 5);
                return;
            }

            Member member = user.getAsMember();

            if (member == null) {
                commandEvent.reply(commandEvent.getResource("message.default.invalidOption"), 5);
                return;
            }

            if (member.getIdLong() == commandEvent.getMember().getIdLong()) {
                commandEvent.reply(commandEvent.getResource("message.steal.self"), 5);
                return;
            }

            EconomyUtil.getMoneyHolder(commandEvent.getGuild().getIdLong(), member.getIdLong(), false).subscribe(targetHolder -> {
                if (targetHolder == null) {
                    commandEvent.reply(commandEvent.getResource("message.steal.notEnoughMoney", member.getAsMention()), 5);
                    return;
                }

                // Leave them poor people alone ong.
                if (!EconomyUtil.hasCash(targetHolder) || targetHolder.getAmount() <= 50) {
                    commandEvent.reply(commandEvent.getResource("message.steal.notEnoughMoney", member.getAsMention()), 5);
                    return;
                }

                double stealAmount = RandomUtils.round(targetHolder.getAmount() * RandomUtils.nextDouble(0.01, 0.25), 2);

                EconomyUtil.getMoneyHolder(commandEvent.getMember()).subscribe(holder -> {
                    if (holder == null) {
                        commandEvent.reply(commandEvent.getResource("message.steal.notEnoughMoney", member.getAsMention()), 5);
                        return;
                    }

                    if (EconomyUtil.pay(targetHolder, holder, stealAmount, false, false)) {
                        commandEvent.reply(commandEvent.getResource("message.steal.success", EconomyUtil.formatMoney(stealAmount), member.getAsMention()), 5);
                    } else {
                        commandEvent.reply(commandEvent.getResource("message.steal.failed", EconomyUtil.formatMoney(stealAmount), member.getAsMention()), 5);
                    }

                    stealTimeout.add(entryString);
                    ThreadUtil.createThread(x -> stealTimeout.remove(entryString), Duration.ofSeconds(delay), false, false);
                });
            });
        });
    }

    /**
     * @inheritDoc
     */
    @Override
    public CommandData getCommandData() {
        return new CommandDataImpl("steal", "command.description.steal")
                .addOption(OptionType.USER, "user", "The user you want to steal money from.", true);
    }

    /**
     * @inheritDoc
     */
    @Override
    public String[] getAlias() {
        return new String[0];
    }
}
