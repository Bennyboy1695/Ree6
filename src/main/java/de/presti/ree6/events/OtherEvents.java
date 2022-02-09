package de.presti.ree6.events;

import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import de.presti.ree6.addons.impl.ChatProtector;
import de.presti.ree6.bot.BotInfo;
import de.presti.ree6.bot.BotState;
import de.presti.ree6.bot.BotUtil;
import de.presti.ree6.bot.Webhook;
import de.presti.ree6.main.Main;
import de.presti.ree6.utils.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.internal.interactions.component.SelectMenuImpl;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OtherEvents extends ListenerAdapter {

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        BotInfo.state = BotState.STARTED;
        Main.getInstance().getLogger().info("Boot up finished!");

        Main.getInstance().getCommandManager().addSlashCommand();

        Main.getInstance().createCheckerThread();

        BotUtil.setActivity(BotInfo.botInstance.getGuilds().size() + " Guilds", Activity.ActivityType.WATCHING);
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        Main.getInstance().getSqlConnector().getSqlWorker().createSettings(event.getGuild().getId());
    }

    @Override
    public void onGuildLeave(@Nonnull GuildLeaveEvent event) {
        Main.getInstance().getSqlConnector().getSqlWorker().deleteAllData(event.getGuild().getId());
    }

    @Override
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {

        AutoRoleHandler.handleMemberJoin(event.getGuild(), event.getMember());

        if (!Main.getInstance().getSqlConnector().getSqlWorker().isWelcomeSetup(event.getGuild().getId()))
            return;

        WebhookMessageBuilder wmb = new WebhookMessageBuilder();

        wmb.setAvatarUrl(BotInfo.botInstance.getSelfUser().getAvatarUrl());
        wmb.setUsername("Welcome!");
        wmb.setContent((Main.getInstance().getSqlConnector().getSqlWorker().getMessage(event.getGuild().getId())).replaceAll("%user_name%", event.getMember().getUser().getName()).replaceAll("%user_mention%", event.getMember().getUser().getAsMention()).replaceAll("%guild_name%", event.getGuild().getName()));

        String[] info = Main.getInstance().getSqlConnector().getSqlWorker().getWelcomeWebhook(event.getGuild().getId());

        Webhook.sendWebhook(null, wmb.build(), Long.parseLong(info[0]), info[1], false);
    }

    @Override
    public void onGuildVoiceJoin(@Nonnull GuildVoiceJoinEvent event) {
        if (!ArrayUtil.voiceJoined.containsKey(event.getMember().getUser())) {
            ArrayUtil.voiceJoined.put(event.getMember().getUser(), System.currentTimeMillis());
        }
        super.onGuildVoiceJoin(event);
    }

    @Override
    public void onGuildVoiceLeave(@Nonnull GuildVoiceLeaveEvent event) {
        if (ArrayUtil.voiceJoined.containsKey(event.getMember().getUser())) {
            int min = TimeUtil.getTimeinMin(TimeUtil.getTimeinSec(ArrayUtil.voiceJoined.get(event.getMember().getUser())));

            int addxp = 0;

            for (int i = 1; i <= min; i++) {
                addxp += RandomUtils.random.nextInt(9) + 1;
            }

            Main.getInstance().getSqlConnector().getSqlWorker().addVoiceXP(event.getGuild().getId(), event.getMember().getUser().getId(), addxp);

            AutoRoleHandler.handleVoiceLevelReward(event.getGuild(), event.getMember());

        }
        super.onGuildVoiceLeave(event);
    }

    @Override
    public void onGuildVoiceGuildDeafen(@NotNull GuildVoiceGuildDeafenEvent event) {
        if (event.getMember() != event.getGuild().getSelfMember())
            return;

        if (!event.isGuildDeafened()) {
            event.getMember().deafen(true).queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        super.onMessageReceived(event);

        if (event.isFromGuild() && event.isFromType(ChannelType.TEXT) && event.getMember() != null) {
            if (!ArrayUtil.messageIDwithMessage.containsKey(event.getMessageId())) {
                ArrayUtil.messageIDwithMessage.put(event.getMessageId(), event.getMessage());
            }

            if (!ArrayUtil.messageIDwithUser.containsKey(event.getMessageId())) {
                ArrayUtil.messageIDwithUser.put(event.getMessageId(), event.getAuthor());
            }

            if (event.getAuthor().isBot())
                return;

            if (ChatProtector.isChatProtectorSetup(event.getGuild().getId())) {
                if (ChatProtector.checkMessage(event.getGuild().getId(), event.getMessage().getContentRaw())) {
                    Main.getInstance().getCommandManager().deleteMessage(event.getMessage(), null);
                    event.getChannel().sendMessage("You can't write that!").queue();
                    return;
                }
            }

            if (!Main.getInstance().getCommandManager().perform(event.getMember(), event.getGuild(), event.getMessage().getContentRaw(), event.getMessage(), event.getTextChannel(), null)) {

                if (!event.getMessage().getMentionedUsers().isEmpty() && event.getMessage().getMentionedUsers().contains(BotInfo.botInstance.getSelfUser())) {
                    event.getChannel().sendMessage("Usage " + Main.getInstance().getSqlConnector().getSqlWorker().getSetting(event.getGuild().getId(), "chatprefix").getStringValue() + "help").queue();
                }

                if (!ArrayUtil.timeout.contains(event.getMember())) {

                    Main.getInstance().getSqlConnector().getSqlWorker().addChatXP(event.getGuild().getId(), event.getAuthor().getId(), (long) RandomUtils.random.nextInt(25) + 1);

                    ArrayUtil.timeout.add(event.getMember());

                    new Thread(() -> {
                        try {
                            Thread.sleep(30000);
                        } catch (InterruptedException ignored) {
                            Main.getInstance().getLogger().error("[OtherEvents] User cool-down Thread interrupted!");
                            Thread.currentThread().interrupt();
                        }

                        ArrayUtil.timeout.remove(event.getMember());

                    }).start();
                }

                AutoRoleHandler.handleChatLevelReward(event.getGuild(), event.getMember());
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // Only accept commands from guilds
        if (!event.isFromGuild() && event.getMember() != null) return;

        event.deferReply(true).queue();

        Main.getInstance().getCommandManager().perform(Objects.requireNonNull(event.getMember()), event.getGuild(), null, null, event.getTextChannel(), event);
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        super.onButtonInteraction(event);
    }

    // TODO finish.

    @Override
    public void onSelectMenuInteraction(@NotNull SelectMenuInteractionEvent event) {
        super.onSelectMenuInteraction(event);

        if (event.getInteraction().getComponent().getId() == null || event.getGuild() == null) return;

        event.deferEdit().queue();

        switch (event.getInteraction().getComponent().getId()) {
            case "setupActionMenu" -> {

                if (event.getMessage().getEmbeds().isEmpty() ||
                        event.getMessage().getEmbeds().get(0) == null ||
                        event.getInteraction().getSelectedOptions().isEmpty()) return;

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getSelectedOptions().get(0).getValue()) {
                    case "log" -> {
                        optionList.add(SelectOption.of("Setup", "logSetup"));

                        if (Main.getInstance().getSqlConnector().getSqlWorker().isLogSetup(event.getGuild().getId()))
                            optionList.add(SelectOption.of("Delete", "logDelete"));

                        optionList.add(SelectOption.of("Back to Menu", "backToSetupMenu"));

                        embedBuilder.setDescription("You can set up our own Audit-Logging which provides all the Information over and Webhook into the Channel of your desire! " +
                                "But ours is not the same as the default Auditions, ours gives your the ability to set what you want to be logged and what not! " +
                                "We also allow you to log Voice Events!");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupLogMenu", "Select your Action", 1, 1, false, optionList))).queue();
                    }

                    case "welcome" -> {
                        optionList.add(SelectOption.of("Setup", "welcomeSetup"));

                        if (Main.getInstance().getSqlConnector().getSqlWorker().isWelcomeSetup(event.getGuild().getId()))
                            optionList.add(SelectOption.of("Delete", "welcomeDelete"));

                        optionList.add(SelectOption.of("Back to Menu", "backToSetupMenu"));

                        embedBuilder.setDescription("You can set up our own Welcome-Messages! " +
                                "You can choice the Welcome-Channel by your own and even configure the Message!");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupWelcomeMenu", "Select your Action", 1, 1, false, optionList))).queue();
                    }

                    case "news" -> {
                        optionList.add(SelectOption.of("Setup", "newsSetup"));

                        if (Main.getInstance().getSqlConnector().getSqlWorker().isNewsSetup(event.getGuild().getId()))
                            optionList.add(SelectOption.of("Delete", "newsDelete"));

                        optionList.add(SelectOption.of("Back to Menu", "backToSetupMenu"));

                        embedBuilder.setDescription("You can set up our own Ree6-News! " +
                                "By setting up Ree6-News on a specific channel your will get a Message in the given Channel, when ever Ree6 gets an update!");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupNewsMenu", "Select your Action", 1, 1, false, optionList))).queue();
                    }

                    case "mute" -> {
                        embedBuilder.setDescription("You can set up our own Mute-System! " +
                                "You can select the Role that Ree6 should give an Mute User!");

                        event.getMessage().editMessageEmbeds(embedBuilder.build()).setActionRows(ActionRow.of(Button.link("https://cp.ree6.de", "Webinterface"))).queue();
                    }

                    case "autorole" -> {
                        embedBuilder.setDescription("You can set up our own Autorole-System! " +
                                "You can select Roles that Users should get upon joining the Server!");

                        event.getMessage().editMessageEmbeds(embedBuilder.build()).setActionRows(ActionRow.of(Button.link("https://cp.ree6.de", "Webinterface"))).queue();
                    }

                    default -> {
                        embedBuilder.setDescription("You somehow selected a Invalid Option? Are you a Wizard?");
                    }
                }
            }

            case "setupLogMenu" -> {
                if (event.getMessage().getEmbeds().isEmpty() ||
                        event.getMessage().getEmbeds().get(0) == null ||
                        event.getInteraction().getSelectedOptions().isEmpty()) return;

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getSelectedOptions().get(0).getValue()) {

                    case "backToSetupMenu" -> {
                        optionList.add(SelectOption.of("Audit-Logging", "log"));
                        optionList.add(SelectOption.of("Welcome-channel", "welcome"));
                        optionList.add(SelectOption.of("News-channel", "news"));
                        optionList.add(SelectOption.of("Mute role", "mute"));
                        optionList.add(SelectOption.of("Autorole", "autrorole"));

                        embedBuilder.setDescription("Which configuration do you want to check out?");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupActionMenu", "Select a configuration Step!", 1, 1, false, optionList))).queue();
                    }

                    case "logSetup" -> {
                        for (TextChannel channel : event.getGuild().getTextChannels()) {
                            optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                        }

                        embedBuilder.setDescription("Which Channel do you want to use as Logging-Channel?");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupLogChannel", "Select a Channel!", 1, 1, false, optionList))).queue();
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription("You somehow selected a Invalid Option? Are you a Wizard?");
                        event.getMessage().editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupWelcomeMenu" -> {
                if (event.getMessage().getEmbeds().isEmpty() ||
                        event.getMessage().getEmbeds().get(0) == null ||
                        event.getInteraction().getSelectedOptions().isEmpty()) return;

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getSelectedOptions().get(0).getValue()) {

                    case "backToSetupMenu" -> {
                        optionList.add(SelectOption.of("Audit-Logging", "log"));
                        optionList.add(SelectOption.of("Welcome-channel", "welcome"));
                        optionList.add(SelectOption.of("News-channel", "news"));
                        optionList.add(SelectOption.of("Mute role", "mute"));
                        optionList.add(SelectOption.of("Autorole", "autrorole"));

                        embedBuilder.setDescription("Which configuration do you want to check out?");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupActionMenu", "Select a configuration Step!", 1, 1, false, optionList))).queue();
                    }

                    case "welcomeSetup" -> {
                        for (TextChannel channel : event.getGuild().getTextChannels()) {
                            optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                        }

                        embedBuilder.setDescription("Which Channel do you want to use as Welcome-Channel?");

                        event.getMessage().editMessageEmbeds(embedBuilder.build())
                                .setActionRows(ActionRow.of(new SelectMenuImpl("setupWelcomeChannel", "Select a Channel!", 1, 1, false, optionList))).queue();
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription("You somehow selected a Invalid Option? Are you a Wizard?");
                        event.getMessage().editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            default -> {
                if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null) return;

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                embedBuilder.setDescription("You somehow selected a Invalid Option? Are you a Wizard?");
                event.getMessage().editMessageEmbeds(embedBuilder.build()).queue();
            }
        }
    }
}
