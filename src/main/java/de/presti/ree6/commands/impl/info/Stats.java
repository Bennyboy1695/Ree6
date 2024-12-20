package de.presti.ree6.commands.impl.info;

import de.presti.ree6.bot.BotConfig;
import de.presti.ree6.bot.BotWorker;
import de.presti.ree6.commands.Category;
import de.presti.ree6.commands.CommandEvent;
import de.presti.ree6.commands.interfaces.Command;
import de.presti.ree6.commands.interfaces.ICommand;
import de.presti.ree6.sql.SQLSession;
import de.presti.ree6.sql.entities.stats.CommandStats;
import de.presti.ree6.sql.entities.stats.GuildCommandStats;
import de.presti.ree6.utils.others.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A command to show you the stats of Ree6.
 */
@Command(name = "stats", description = "command.description.stats", category = Category.INFO)
public class Stats implements ICommand {

    boolean firstBoot;

    /**
     * @inheritDoc
     */
    @Override
    public void onPerform(CommandEvent commandEvent) {
        commandEvent.delete();

        long start = System.currentTimeMillis();

        Message message = commandEvent.isSlashCommand()
                ? commandEvent.getInteractionHook().sendMessage(commandEvent.getResource("label.loading")).complete()
                : commandEvent.getChannel().sendMessage(commandEvent.getResource("label.loading")).complete();

        long ping = System.currentTimeMillis() - start;
        long computeTimeStart = System.currentTimeMillis();
        EmbedBuilder em = new EmbedBuilder();

        em.setAuthor(commandEvent.getGuild().getJDA().getSelfUser().getName(), BotConfig.getWebsite(),
                commandEvent.getGuild().getJDA().getSelfUser().getEffectiveAvatarUrl());
        em.setTitle(commandEvent.getResource("label.statistics"));
        em.setThumbnail(commandEvent.getGuild().getJDA().getSelfUser().getEffectiveAvatarUrl());
        em.setColor(BotConfig.getMainColor());

        long memberCount = firstBoot ? BotWorker.getShardManager().getUsers().size() : BotWorker.getShardManager().getUserCache().size();
        firstBoot = true;

        em.addField("**" + commandEvent.getResource("label.serverStats") + ":**", "", true);
        em.addField("**" + commandEvent.getResource("label.guilds") + "**", BotWorker.getShardManager().getGuilds().size() + "", true);
        em.addField("**" + commandEvent.getResource("label.users") + "**", String.valueOf(memberCount), true);

        em.addField("**" + commandEvent.getResource("label.botStats") + ":**", "", true);
        em.addField("**" + commandEvent.getResource("label.version") + "**", BotWorker.getBuild() + "-" + BotWorker.getVersion().getName().toUpperCase()
                + " [[" + BotWorker.getCommit() + "](" + BotWorker.getRepository().replace(".git", "") + "/commit/" + BotWorker.getCommit() + ")]", true);
        em.addField("**" + commandEvent.getResource("label.uptime") + "**", TimeUtil.getTime(BotWorker.getStartTime()), true);

        em.addField("**" + commandEvent.getResource("label.discordStats") + ":**", "", true);
        em.addField("**" + commandEvent.getResource("label.gatewayTime") + "**", BotWorker.getShardManager().getAverageGatewayPing() + "ms", true);
        em.addField("**" + commandEvent.getResource("label.shardAmount") + "**", BotWorker.getShardManager().getShards().size() + " " + commandEvent.getResource("label.shards"), true);

        em.addField("**" + commandEvent.getResource("label.networkStats") + ":**", "", true);
        em.addField("**" + commandEvent.getResource("label.responseTime") + "**", (Integer.parseInt((ping) + "")) + "ms", true);
        em.addField("**" + commandEvent.getResource("label.systemDate") + "**", new SimpleDateFormat("dd.MM.yyyy HH:mm").format(new Date()), true);

        StringBuilder end = new StringBuilder();

        SQLSession.getSqlConnector().getSqlWorker().getStats(commandEvent.getGuild().getIdLong()).subscribe(stats -> {
            for (GuildCommandStats values : stats) {
                end.append(values.getCommand()).append(" - ").append(values.getUses()).append("\n");
            }

            StringBuilder end2 = new StringBuilder();

            SQLSession.getSqlConnector().getSqlWorker().getStatsGlobal().subscribe(statsGlobal -> {
                for (CommandStats values : statsGlobal) {
                    end2.append(values.getCommand()).append(" - ").append(values.getUses()).append("\n");
                }

                em.addField("**" + commandEvent.getResource("label.commandStats") + ":**", "", true);
                em.addField("**" + commandEvent.getResource("label.topCommands") + "**", end.toString(), true);
                em.addField("**" + commandEvent.getResource("label.overallTopCommands") + "**", end2.toString(), true);

                em.setFooter(commandEvent.getGuild().getName() + " - " + BotConfig.getAdvertisement(), commandEvent.getGuild().getIconUrl());

                MessageEditBuilder messageEditBuilder = new MessageEditBuilder();

                messageEditBuilder.setContent("");

                long computeTime = System.currentTimeMillis() - computeTimeStart;

                if (BotConfig.isDebug()) {
                    em.addField("**DEV ONLY**", "", true);
                    em.addField("**Compute Time**", computeTime + "ms", true);
                    em.addField("**App Installs**", "" + BotWorker.getShardManager().retrieveApplicationInfo().complete().getUserInstallCount(), true);
                }

                messageEditBuilder.setEmbeds(em.build());

                commandEvent.update(message, messageEditBuilder.build());
            });
        });
    }

    /**
     * @inheritDoc
     */
    @Override
    public CommandData getCommandData() {
        return null;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String[] getAlias() {
        return new String[0];
    }
}
