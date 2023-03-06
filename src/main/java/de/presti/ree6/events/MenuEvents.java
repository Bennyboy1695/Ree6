package de.presti.ree6.events;

import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import de.presti.ree6.bot.BotWorker;
import de.presti.ree6.bot.util.WebhookUtil;
import de.presti.ree6.language.Language;
import de.presti.ree6.language.LanguageService;
import de.presti.ree6.main.Main;
import de.presti.ree6.sql.SQLSession;
import de.presti.ree6.sql.entities.Suggestions;
import de.presti.ree6.sql.entities.TemporalVoicechannel;
import de.presti.ree6.sql.entities.Tickets;
import de.presti.ree6.sql.entities.stats.ChannelStats;
import de.presti.ree6.sql.entities.webhook.Webhook;
import de.presti.ree6.utils.apis.YouTubeAPIHandler;
import de.presti.ree6.utils.data.Data;
import de.presti.wrapper.entities.channel.ChannelResult;
import masecla.reddit4j.objects.subreddit.RedditSubreddit;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.internal.interactions.component.StringSelectMenuImpl;
import org.jetbrains.annotations.NotNull;
import twitter4j.TwitterException;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * EventHandler for Menu Events.
 */
public class MenuEvents extends ListenerAdapter {

    /**
     * @inheritDoc
     */
    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        super.onButtonInteraction(event);

        switch (event.getComponentId()) {
            case "re_feedback" -> {
                Modal.Builder builder = Modal.create("re_feedback_modal", LanguageService.getByGuild(event.getGuild(), "label.feedback"));
                builder.addActionRow(TextInput.create("re_feedback_text", LanguageService.getByGuild(event.getGuild(), "label.feedback"), TextInputStyle.PARAGRAPH).setRequired(true).setMaxLength(2042).setMinLength(16).build());
                event.replyModal(builder.build()).queue();
            }

            case "re_suggestion" -> {
                Modal.Builder builder = Modal.create("re_suggestion_modal", LanguageService.getByGuild(event.getGuild(), "label.suggestion"));
                builder.addActionRow(TextInput.create("re_suggestion_text", LanguageService.getByGuild(event.getGuild(), "label.suggestion"), TextInputStyle.PARAGRAPH).setRequired(true).setMaxLength(2042).setMinLength(16).build());
                event.replyModal(builder.build()).queue();
            }

            case "re_ticket_open" -> {
                event.deferReply(true).queue();
                Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(), "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", event.getGuild().getId()));

                if (tickets != null) {
                    Category category = event.getGuild().getCategoryById(tickets.getTicketCategory());

                    if (category != null) {
                        if (category.getTextChannels().stream().anyMatch(c -> c.getName().contains(event.getUser().getName()))) {
                            event.getHook().sendMessage(LanguageService.getByGuild(event.getGuild(), "message.ticket.alreadyOpen")).queue();
                            return;
                        }

                        category.createTextChannel("ticket-" + event.getUser().getName())
                                .setTopic(event.getUser().getId())
                                .syncPermissionOverrides()
                                .addPermissionOverride(event.getMember(), List.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_EMBED_LINKS), List.of())
                                .queue(channel -> {
                                    MessageCreateBuilder messageCreateBuilder = new MessageCreateBuilder();
                                    messageCreateBuilder.setEmbeds(new EmbedBuilder().setTitle(LanguageService.getByGuild(event.getGuild(), "label.ticket")).setDescription("Welcome to your Ticket!").setThumbnail(event.getMember().getEffectiveAvatarUrl()).setColor(Color.GREEN).setTimestamp(Instant.now()).build());
                                    messageCreateBuilder.addActionRow(Button.primary("re_ticket_close", LanguageService.getByGuild(event.getGuild(), "label.closeTicket")));
                                    Main.getInstance().getCommandManager().sendMessage(messageCreateBuilder.build(), channel);
                                    event.getHook().sendMessage(LanguageService.getByGuild(event.getGuild(), "message.ticket.created", channel.getAsMention())).queue();
                                });
                        tickets.setTicketCount(tickets.getTicketCount() + 1);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(tickets);
                    } else {
                        event.getHook().sendMessage(LanguageService.getByGuild(event.getGuild(), "message.ticket.categoryNotFound")).queue();
                    }
                }
            }

            case "re_ticket_close" -> {
                event.deferReply(true).queue();

                Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(), "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", event.getGuild().getId()));

                if (tickets != null) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Ree6 Ticket transcript")
                            .append(" ")
                            .append(ZonedDateTime.now().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG)))
                            .append("\n")
                            .append("\n");


                    for (Message message : event.getChannel().asTextChannel().getIterableHistory().reverse()) {
                        stringBuilder
                                .append("[")
                                .append(message.getTimeCreated().toZonedDateTime().format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)))
                                .append("]")
                                .append(" ")
                                .append(message.getAuthor().getAsTag())
                                .append(" ")
                                .append("->")
                                .append(" ")
                                .append(message.getContentRaw());

                        if (!message.getAttachments().isEmpty()) {
                            for (Message.Attachment attachment : message.getAttachments()) {
                                stringBuilder.append("\n").append(attachment.getUrl());
                            }
                        }

                        stringBuilder.append("\n");
                    }

                    stringBuilder.append("\n").append("Closed by").append(" ").append(event.getUser().getAsTag());

                    WebhookMessageBuilder webhookMessageBuilder = new WebhookMessageBuilder();
                    webhookMessageBuilder.setAvatarUrl(event.getJDA().getSelfUser().getEffectiveAvatarUrl());
                    webhookMessageBuilder.setUsername("Ree6-Tickets");

                    WebhookEmbedBuilder webhookEmbedBuilder = new WebhookEmbedBuilder();

                    webhookEmbedBuilder.setDescription("Here is the transcript of the ticket " + tickets.getTicketCount() + "!");
                    webhookEmbedBuilder.setFooter(new WebhookEmbed.EmbedFooter(event.getGuild().getName() + " " + Data.ADVERTISEMENT, event.getGuild().getIconUrl()));
                    webhookEmbedBuilder.setColor(BotWorker.randomEmbedColor().getRGB());

                    webhookMessageBuilder.addEmbeds(webhookEmbedBuilder.build());
                    webhookMessageBuilder.addFile(tickets.getTicketCount() + "_transcript.txt", stringBuilder.toString().getBytes(StandardCharsets.UTF_8));

                    WebhookUtil.sendWebhook(null, webhookMessageBuilder.build(), tickets.getLogChannelId(), tickets.getLogChannelWebhookToken(), false);

                    event.getHook().sendMessage(LanguageService.getByGuild(event.getGuild(), "message.ticket.close")).queue();
                    event.getChannel().delete().delay(2, TimeUnit.SECONDS).queue();
                }
            }
        }
    }


    /**
     * @inheritDoc
     */
    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        super.onModalInteraction(event);


        switch (event.getModalId()) {
            case "re_feedback_modal" -> {
                // TODO:: decide if this is going to be send via webhook or stored in the database.
            }

            case "re_suggestion_modal" -> {
                Suggestions suggestions = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Suggestions(), "SELECT * FROM Suggestions WHERE guildId=:gid", Map.of("gid", event.getGuild().getId()));

                event.deferReply(true).queue();

                if (suggestions != null) {
                    MessageChannel messageChannel = (MessageChannel) event.getGuild().getGuildChannelById(suggestions.getChannelId());

                    if (messageChannel == null) return;

                    EmbedBuilder embedBuilder = new EmbedBuilder();
                    embedBuilder.setTitle(LanguageService.getByGuild(event.getGuild(), "label.suggestion"));
                    embedBuilder.setColor(Color.ORANGE);
                    embedBuilder.setThumbnail(event.getUser().getEffectiveAvatarUrl());
                    embedBuilder.setDescription("```" + event.getValue("re_suggestion_text").getAsString() + "```");
                    embedBuilder.setFooter(LanguageService.getByGuild(event.getGuild(), "message.suggestion.footer", event.getUser().getAsTag()), event.getUser().getAvatarUrl());
                    embedBuilder.setTimestamp(Instant.now());
                    Main.getInstance().getCommandManager().sendMessage(embedBuilder, messageChannel);
                    Main.getInstance().getCommandManager().sendMessage(LanguageService.getByGuild(event.getGuild(), "message.suggestion.sent"), null, event.getInteraction().getHook());
                } else {
                    Main.getInstance().getCommandManager().sendMessage(LanguageService.getByGuild(event.getGuild(), "message.suggestion.notSetup"), null, event.getInteraction().getHook());
                }
            }

            case "statisticsSetupTwitchModal" -> {
                ModalMapping modalMapping = event.getValue("twitchChannelName");

                if (modalMapping == null) return;

                if (event.getGuild() == null) return;

                String twitchUsername = modalMapping.getAsString();

                java.util.List<Category> categories = event.getGuild().getCategoriesByName(LanguageService.getByGuild(event.getGuild(), "label.statistics"), true);

                Category category;

                if (categories.isEmpty()) {
                    category = event.getGuild().createCategory(LanguageService.getByGuild(event.getGuild(), "label.statistics")).complete();
                } else {
                    category = categories.get(0);
                }

                String channelId = Main.getInstance().getNotifier().getTwitchClient().getHelix().getUsers(null, null, Collections.singletonList(twitchUsername)).execute().getUsers().get(0).getId();
                event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.twitchCountName", Main.getInstance().getNotifier().getTwitchClient().getHelix().getFollowers(null, null, channelId, null, 20).execute().getTotal()), category).queue(voiceChannel -> {
                    ChannelStats channelStats = SQLSession.getSqlConnector().getSqlWorker().getEntity(new ChannelStats(), "SELECT * FROM ChannelStats WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));
                    if (channelStats != null) {

                        if (channelStats.getTwitchFollowerChannelId() != null) {
                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getTwitchFollowerChannelId());

                            if (voiceChannel3 != null)
                                voiceChannel3.delete().queue();
                        }

                        channelStats.setTwitchFollowerChannelId(voiceChannel.getId());
                        channelStats.setTwitchFollowerChannelUsername(twitchUsername);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerTwitchChannel(twitchUsername);
                    } else {
                        channelStats = new ChannelStats(event.getGuild().getId(),
                                null,
                                null,
                                null,
                                voiceChannel.getId(),
                                twitchUsername,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerTwitchChannel(twitchUsername);
                    }
                });

                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.twitchSuccess"));
                event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
            }

            case "statisticsSetupYouTubeModal" -> {
                ModalMapping modalMapping = event.getValue("youtubeChannelName");

                if (modalMapping == null) return;

                if (event.getGuild() == null) return;

                String youtubeChannelName = modalMapping.getAsString();

                java.util.List<Category> categories = event.getGuild().getCategoriesByName(LanguageService.getByGuild(event.getGuild(), "label.statistics"), true);

                Category category;

                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.youtubeSuccess"));

                if (categories.isEmpty()) {
                    category = event.getGuild().createCategory(LanguageService.getByGuild(event.getGuild(), "label.statistics")).complete();
                } else {
                    category = categories.get(0);
                }

                ChannelResult youTubeChannel;
                try {
                    if (YouTubeAPIHandler.getInstance().isValidChannelId(youtubeChannelName)) {
                        youTubeChannel = YouTubeAPIHandler.getInstance().getYouTubeChannelById(youtubeChannelName);
                    } else {
                        youTubeChannel = YouTubeAPIHandler.getInstance().getYouTubeChannelBySearch(youtubeChannelName);
                    }
                } catch (Exception e) {
                    embedBuilder
                            .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                            .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                            .setColor(Color.RED)
                            .setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.retrievalError"));
                    event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    return;
                }

                if (youTubeChannel == null) {
                    embedBuilder = embedBuilder
                            .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                            .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                            .setColor(Color.RED)
                            .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.youtubeNotFound"));
                    event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    return;
                }

                event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.youtubeCountName", youTubeChannel.getSubscriberCountText()), category).queue(voiceChannel -> {
                    ChannelStats channelStats = SQLSession.getSqlConnector().getSqlWorker().getEntity(new ChannelStats(), "SELECT * FROM ChannelStats WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));
                    if (channelStats != null) {

                        if (channelStats.getYoutubeSubscribersChannelId() != null) {
                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getYoutubeSubscribersChannelId());

                            if (voiceChannel3 != null)
                                voiceChannel3.delete().queue();
                        }

                        channelStats.setYoutubeSubscribersChannelId(voiceChannel.getId());
                        channelStats.setYoutubeSubscribersChannelUsername(youtubeChannelName);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerYouTubeChannel(youtubeChannelName);
                    } else {
                        channelStats = new ChannelStats(event.getGuild().getId(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                voiceChannel.getId(),
                                youtubeChannelName,
                                null,
                                null);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerYouTubeChannel(youtubeChannelName);
                    }
                });

                embedBuilder = embedBuilder
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.youtubeSuccess"));
                event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
            }

            case "statisticsSetupRedditModal" -> {
                ModalMapping modalMapping = event.getValue("subredditName");

                if (modalMapping == null) return;

                if (event.getGuild() == null) return;

                String subredditName = modalMapping.getAsString();

                java.util.List<Category> categories = event.getGuild().getCategoriesByName(LanguageService.getByGuild(event.getGuild(), "label.statistics"), true);

                Category category;

                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.redditSuccess"));

                if (categories.isEmpty()) {
                    category = event.getGuild().createCategory(LanguageService.getByGuild(event.getGuild(), "label.statistics")).complete();
                } else {
                    category = categories.get(0);
                }

                RedditSubreddit subreddit;
                try {
                    subreddit = Main.getInstance().getNotifier().getSubreddit(subredditName);
                } catch (IOException | InterruptedException e) {
                    embedBuilder
                            .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                            .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                            .setColor(Color.RED)
                            .setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.retrievalError"));
                    event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    return;
                }

                event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.redditCountName", subreddit.getActiveUserCount()), category).queue(voiceChannel -> {
                    ChannelStats channelStats = SQLSession.getSqlConnector().getSqlWorker().getEntity(new ChannelStats(), "SELECT * FROM ChannelStats WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));
                    if (channelStats != null) {

                        if (channelStats.getSubredditMemberChannelId() != null) {
                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getSubredditMemberChannelId());

                            if (voiceChannel3 != null)
                                voiceChannel3.delete().queue();
                        }

                        channelStats.setSubredditMemberChannelId(voiceChannel.getId());
                        channelStats.setSubredditMemberChannelSubredditName(subredditName);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerSubreddit(subredditName);
                    } else {
                        channelStats = new ChannelStats(event.getGuild().getId(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                voiceChannel.getId(),
                                subredditName);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerSubreddit(subredditName);
                    }
                });

                embedBuilder = embedBuilder
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.redditSuccess"));
                event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
            }

            case "statisticsSetupTwitterModal" -> {
                ModalMapping modalMapping = event.getValue("twitterName");

                if (modalMapping == null) return;

                if (event.getGuild() == null) return;

                String twitterName = modalMapping.getAsString();

                java.util.List<Category> categories = event.getGuild().getCategoriesByName(LanguageService.getByGuild(event.getGuild(), "label.statistics"), true);

                Category category;

                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.twitterSuccess"));

                if (categories.isEmpty()) {
                    category = event.getGuild().createCategory(LanguageService.getByGuild(event.getGuild(), "label.statistics")).complete();
                } else {
                    category = categories.get(0);
                }

                twitter4j.User twitterUser;
                try {
                    twitterUser = Main.getInstance().getNotifier().getTwitterClient().showUser(twitterName);
                } catch (TwitterException e) {
                    embedBuilder
                            .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                            .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                            .setColor(Color.RED)
                            .setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.retrievalError"));
                    event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    return;
                }

                event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.twitterCountName", twitterUser.getFollowersCount()), category).queue(voiceChannel -> {
                    ChannelStats channelStats = SQLSession.getSqlConnector().getSqlWorker().getEntity(new ChannelStats(), "SELECT * FROM ChannelStats WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));
                    if (channelStats != null) {

                        if (channelStats.getTwitterFollowerChannelId() != null) {
                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getTwitterFollowerChannelId());

                            if (voiceChannel3 != null)
                                voiceChannel3.delete().queue();
                        }

                        channelStats.setTwitterFollowerChannelId(voiceChannel.getId());
                        channelStats.setTwitterFollowerChannelUsername(twitterName);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerTwitterUser(twitterName);
                    } else {
                        channelStats = new ChannelStats(event.getGuild().getId(),
                                null,
                                null,
                                null,
                                voiceChannel.getId(),
                                twitterName,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerTwitterUser(twitterName);
                    }
                });

                embedBuilder = embedBuilder
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.twitterSuccess"));
                event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
            }

            case "statisticsSetupInstagramModal" -> {
                ModalMapping modalMapping = event.getValue("instagramName");

                if (modalMapping == null) return;

                if (event.getGuild() == null) return;

                String instagramName = modalMapping.getAsString();

                java.util.List<Category> categories = event.getGuild().getCategoriesByName(LanguageService.getByGuild(event.getGuild(), "label.statistics"), true);

                Category category;

                EmbedBuilder embedBuilder = new EmbedBuilder()
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.instagramSuccess"));

                if (categories.isEmpty()) {
                    category = event.getGuild().createCategory(LanguageService.getByGuild(event.getGuild(), "label.statistics")).complete();
                } else {
                    category = categories.get(0);
                }

                com.github.instagram4j.instagram4j.models.user.User instagramUser;
                try {
                    instagramUser = Main.getInstance().getNotifier().getInstagramClient().getActions().users().findByUsername(instagramName).get().getUser();
                } catch (ExecutionException | InterruptedException e) {
                    embedBuilder
                            .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                            .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                            .setColor(Color.RED)
                            .setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.retrievalError"));
                    event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    return;
                }

                event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.instagramCountName", instagramUser.getFollower_count()), category).queue(voiceChannel -> {
                    ChannelStats channelStats = SQLSession.getSqlConnector().getSqlWorker().getEntity(new ChannelStats(), "SELECT * FROM ChannelStats WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));
                    if (channelStats != null) {

                        if (channelStats.getInstagramFollowerChannelId() != null) {
                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getInstagramFollowerChannelId());

                            if (voiceChannel3 != null)
                                voiceChannel3.delete().queue();
                        }

                        channelStats.setInstagramFollowerChannelId(voiceChannel.getId());
                        channelStats.setInstagramFollowerChannelUsername(instagramName);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerInstagramUser(instagramName);
                    } else {
                        channelStats = new ChannelStats(event.getGuild().getId(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                voiceChannel.getId(),
                                instagramName,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null);
                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                        Main.getInstance().getNotifier().registerInstagramUser(instagramName);
                    }
                });

                embedBuilder = embedBuilder
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.setupMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.GREEN)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.instagramSuccess"));
                event.deferEdit().setEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
            }

            default -> {
                event.deferEdit().setEmbeds(new EmbedBuilder()
                        .setTitle(LanguageService.getByGuild(event.getGuild(), "label.unknownMenu"))
                        .setFooter(event.getGuild().getName() + " - " + Data.ADVERTISEMENT, event.getGuild().getIconUrl())
                        .setColor(Color.RED)
                        .setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.unknownMenu"))
                        .build()).setComponents(new ArrayList<>()).queue();
            }
        }
    }

    /**
     * @inheritDoc
     */
    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        super.onStringSelectInteraction(event);

        if (event.getInteraction().getComponent().getId() == null ||
                event.getGuild() == null)
            return;

        if (event.getMessage().getEmbeds().isEmpty() ||
                event.getMessage().getEmbeds().get(0) == null ||
                event.getInteraction().getSelectedOptions().isEmpty())
            return;

        if (event.getInteraction().getValues().isEmpty())
            return;

        switch (event.getInteraction().getComponent().getId()) {
            case "setupActionMenu" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                java.util.List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getValues().get(0)) {
                    case "lang" -> {

                        for (DiscordLocale locale : LanguageService.getSupported()) {
                            optionList.add(SelectOption.of(locale.getLanguageName(), locale.getLocale()));
                        }

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.setup.steps.lang"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupLangMenu", LanguageService.getByGuild(event.getGuild(), "message.default.actionRequired"), 1, 1, false, optionList)).queue();
                    }

                    case "log" -> {
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setup"), "logSetup"));

                        if (SQLSession.getSqlConnector().getSqlWorker().isLogSetup(event.getGuild().getId()))
                            optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.delete"), "logDelete"));

                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.backToMenu"), "backToSetupMenu"));

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.setup.steps.auditLog"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupLogMenu", LanguageService.getByGuild(event.getGuild(), "message.default.actionRequired"), 1, 1, false, optionList)).queue();
                    }

                    case "welcome" -> {
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setup"), "welcomeSetup"));

                        if (SQLSession.getSqlConnector().getSqlWorker().isWelcomeSetup(event.getGuild().getId()))
                            optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.delete"), "welcomeDelete"));

                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setImage"), "welcomeImage"));

                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.backToMenu"), "backToSetupMenu"));

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.setup.steps.welcome"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupWelcomeMenu", LanguageService.getByGuild(event.getGuild(), "message.default.actionRequired"), 1, 1, false, optionList)).queue();
                    }

                    case "autorole" -> {
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.setup.steps.autoRole"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(Button.link("https://cp.ree6.de", "Webinterface")).queue();
                    }

                    case "tempvoice" -> {
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setup"), "tempVoiceSetup"));

                        if (SQLSession.getSqlConnector().getSqlWorker().getEntity(new TemporalVoicechannel(), "SELECT * FROM TemporalVoicechannel WHERE GID=:gid", Map.of("gid", event.getGuild().getId())) != null)
                            optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.delete"), "tempVoiceDelete"));

                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.backToMenu"), "backToSetupMenu"));

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.setup.steps.temporalVoice"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupTempVoiceMenu", LanguageService.getByGuild(event.getGuild(), "message.default.actionRequired"), 1, 1, false, optionList)).queue();
                    }

                    case "statistics" -> {
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setupMemberStatistics"), "statisticsSetupMember"));
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setupTwitchStatistics"), "statisticsSetupTwitch"));
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setupYoutubeStatistics"), "statisticsSetupYouTube"));
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setupRedditStatistics"), "statisticsSetupReddit"));
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setupTwitterStatistics"), "statisticsSetupTwitter"));
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setupInstagramStatistics"), "statisticsSetupInstagram"));

                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.backToMenu"), "backToSetupMenu"));

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.setup"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupStatisticsMenu", LanguageService.getByGuild(event.getGuild(), "message.default.actionRequired"), 1, 1, false, optionList)).queue();
                    }

                    case "tickets" -> {
                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.setup"), "ticketsSetup"));

                        if (SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(), "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", event.getGuild().getId())) != null)
                            optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.delete"), "ticketsDelete"));

                        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.backToMenu"), "backToSetupMenu"));

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.ticket.setup"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupTicketsMenu", LanguageService.getByGuild(event.getGuild(), "message.default.actionRequired"), 1, 1, false, optionList)).queue();
                    }

                    default -> {
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                        event.editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupStatisticsMenu" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                switch (event.getInteraction().getValues().get(0)) {

                    case "backToSetupMenu" -> sendDefaultChoice(event);

                    case "statisticsSetupMember" -> {
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.statistics.memberSuccess"));
                        embedBuilder.setColor(Color.GREEN);
                        event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                        java.util.List<Category> categories = event.getGuild().getCategoriesByName(LanguageService.getByGuild(event.getGuild(), "label.statistics"), true);

                        Category category;

                        if (categories.isEmpty()) {
                            category = event.getGuild().createCategory(LanguageService.getByGuild(event.getGuild(), "label.statistics")).complete();
                        } else {
                            category = categories.get(0);
                        }

                        event.getGuild().loadMembers().onSuccess(members -> event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.overallMembersName", event.getGuild().getMemberCount()), category).queue(voiceChannel -> {
                            voiceChannel.getManager().setUserLimit(0).queue();
                            event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.realMembersName", members.stream().filter(member -> !member.getUser().isBot()).count()), category).queue(voiceChannel1 -> {
                                voiceChannel1.getManager().setUserLimit(0).queue();
                                event.getGuild().createVoiceChannel(LanguageService.getByGuild(event.getGuild(), "label.botMembersName", members.stream().filter(member -> member.getUser().isBot()).count()), category).queue(voiceChannel2 -> {
                                    voiceChannel2.getManager().setUserLimit(0).queue();
                                    ChannelStats channelStats = SQLSession.getSqlConnector().getSqlWorker().getEntity(new ChannelStats(), "SELECT * FROM ChannelStats WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));
                                    if (channelStats != null) {
                                        if (channelStats.getMemberStatsChannelId() != null) {
                                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getMemberStatsChannelId());

                                            if (voiceChannel3 != null)
                                                voiceChannel3.delete().queue();
                                        }
                                        if (channelStats.getRealMemberStatsChannelId() != null) {
                                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getRealMemberStatsChannelId());

                                            if (voiceChannel3 != null)
                                                voiceChannel3.delete().queue();
                                        }
                                        if (channelStats.getBotMemberStatsChannelId() != null) {
                                            VoiceChannel voiceChannel3 = event.getGuild().getVoiceChannelById(channelStats.getBotMemberStatsChannelId());

                                            if (voiceChannel3 != null)
                                                voiceChannel3.delete().queue();
                                        }
                                        channelStats.setMemberStatsChannelId(voiceChannel.getId());
                                        channelStats.setRealMemberStatsChannelId(voiceChannel1.getId());
                                        channelStats.setBotMemberStatsChannelId(voiceChannel2.getId());
                                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                                    } else {
                                        channelStats = new ChannelStats(event.getGuild().getId(),
                                                voiceChannel.getId(),
                                                voiceChannel1.getId(),
                                                voiceChannel2.getId(),
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null);
                                        SQLSession.getSqlConnector().getSqlWorker().updateEntity(channelStats);
                                    }
                                });
                            });
                        }));
                    }

                    case "statisticsSetupTwitch" -> {
                        TextInput input = TextInput.create("twitchChannelName", LanguageService.getByGuild(event.getGuild(), "label.channelName"), TextInputStyle.SHORT).setMinLength(1).setMaxLength(50).setRequired(true).setPlaceholder("Enter the Twitch Channel name here!").build();

                        Modal modal = Modal.create("statisticsSetupTwitchModal", LanguageService.getByGuild(event.getGuild(), "label.setupTwitchStatistics")).addActionRow(input).build();

                        event.replyModal(modal).queue();
                    }

                    case "statisticsSetupYouTube" -> {
                        TextInput input = TextInput.create("youtubeChannelName", LanguageService.getByGuild(event.getGuild(), "label.channelName"), TextInputStyle.SHORT).setMinLength(1).setMaxLength(50).setRequired(true).setPlaceholder("Enter the YouTube Channel name here!").build();

                        Modal modal = Modal.create("statisticsSetupYouTubeModal", LanguageService.getByGuild(event.getGuild(), "label.setupYoutubeStatistics")).addActionRow(input).build();

                        event.replyModal(modal).queue();
                    }

                    case "statisticsSetupReddit" -> {
                        TextInput input = TextInput.create("subredditName", LanguageService.getByGuild(event.getGuild(), "label.subreddit"), TextInputStyle.SHORT).setMinLength(1).setMaxLength(50).setRequired(true).setPlaceholder("Enter the Subreddit name here!").build();

                        Modal modal = Modal.create("statisticsSetupRedditModal", LanguageService.getByGuild(event.getGuild(), "label.setupRedditStatistics")).addActionRow(input).build();

                        event.replyModal(modal).queue();
                    }

                    case "statisticsSetupTwitter" -> {
                        TextInput input = TextInput.create("twitterName", LanguageService.getByGuild(event.getGuild(), "label.name"), TextInputStyle.SHORT).setMinLength(1).setMaxLength(50).setRequired(true).setPlaceholder("Enter the Twitter name here!").build();

                        Modal modal = Modal.create("statisticsSetupTwitterModal", LanguageService.getByGuild(event.getGuild(), "label.setupTwitterStatistics")).addActionRow(input).build();

                        event.replyModal(modal).queue();
                    }

                    case "statisticsSetupInstagram" -> {
                        TextInput input = TextInput.create("instagramName", LanguageService.getByGuild(event.getGuild(), "label.name"), TextInputStyle.SHORT).setMinLength(1).setMaxLength(50).setRequired(true).setPlaceholder("Enter the Instagram name here!").build();

                        Modal modal = Modal.create("statisticsSetupInstagramModal", LanguageService.getByGuild(event.getGuild(), "label.setupInstagramStatistics")).addActionRow(input).build();

                        event.replyModal(modal).queue();
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                        event.editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupTicketsMenu" -> {
                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                switch (event.getInteraction().getValues().get(0)) {

                    case "backToSetupMenu" -> sendDefaultChoice(event);

                    case "ticketsSetup" -> {
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.ticket.setupDescription"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(Collections.emptyList()).queue();
                    }

                    case "ticketsDelete" -> {
                        Tickets tickets = SQLSession.getSqlConnector().getSqlWorker().getEntity(new Tickets(), "SELECT * FROM Tickets WHERE GUILDID=:gid", Map.of("gid", event.getGuild().getId()));

                        if (tickets != null) {
                            embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.ticket.deleted"));
                            embedBuilder.setColor(Color.GREEN);
                            event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                            SQLSession.getSqlConnector().getSqlWorker().deleteEntity(tickets);
                        }
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                        event.editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupTempVoicechannel" -> {
                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                String value = event.getInteraction().getValues().get(0);

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                if (value.equalsIgnoreCase("more")) {
                    java.util.List<SelectOption> optionList = new ArrayList<>();

                    for (VoiceChannel channel : event.getGuild().getVoiceChannels().stream().skip(24).toList()) {
                        optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                    }

                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.temporalVoice.setupDescription"));

                    event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupTempVoicechannel", LanguageService.getByGuild(event.getGuild(), "label.selectChannel"), 1, 1, false, optionList)).queue();
                    return;
                }

                VoiceChannel voiceChannel = event.getGuild().getVoiceChannelById(value);

                if (voiceChannel != null) {
                    SQLSession.getSqlConnector().getSqlWorker().updateEntity(new TemporalVoicechannel(event.getGuild().getId(), voiceChannel.getId()));
                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.temporalVoice.setupSuccess"));
                    embedBuilder.setColor(Color.GREEN);
                    event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                } else {
                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOptionChannel"));
                    event.editMessageEmbeds(embedBuilder.build()).queue();
                }
            }

            case "setupTempVoiceMenu" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                java.util.List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getValues().get(0)) {

                    case "backToSetupMenu" -> sendDefaultChoice(event);

                    case "tempVoiceSetup" -> {
                        for (VoiceChannel channel : event.getGuild().getVoiceChannels()) {
                            if (optionList.size() == 24) {
                                optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.more"), "more"));
                                break;
                            }

                            optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                        }

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.temporalVoice.setupDescription"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupTempVoicechannel", LanguageService.getByGuild(event.getGuild(), "label.selectChannel"), 1, 1, false, optionList)).queue();
                    }

                    case "tempVoiceDelete" -> {
                        TemporalVoicechannel temporalVoicechannel = SQLSession.getSqlConnector().getSqlWorker().getEntity(new TemporalVoicechannel(), "SELECT * FROM TemporalVoicechannel WHERE GID=:gid", Map.of("gid", event.getGuild().getId()));

                        if (temporalVoicechannel != null) {
                            embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.temporalVoice.deleted"));
                            embedBuilder.setColor(Color.GREEN);
                            event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                            SQLSession.getSqlConnector().getSqlWorker().deleteEntity(temporalVoicechannel);
                        }
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                        event.editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupLangMenu" -> {
                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                DiscordLocale selectedLocale = DiscordLocale.from(event.getInteraction().getValues().get(0));

                if (selectedLocale != DiscordLocale.UNKNOWN && LanguageService.getSupported().contains(selectedLocale)) {
                    Language language = LanguageService.languageResources.get(selectedLocale);
                    SQLSession.getSqlConnector().getSqlWorker().setSetting(event.getGuild().getId(), "configuration_language", selectedLocale.getLocale());
                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.lang.setupSuccess", language.getName() + " by " + language.getAuthor()));
                    embedBuilder.setColor(Color.GREEN);
                    event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                } else {
                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                    event.editMessageEmbeds(embedBuilder.build()).queue();
                }
            }

            case "setupLogMenu" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                java.util.List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getValues().get(0)) {

                    case "backToSetupMenu" -> sendDefaultChoice(event);

                    case "logSetup" -> {
                        for (TextChannel channel : event.getGuild().getTextChannels()) {
                            if (optionList.size() == 24) {
                                optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.more"), "more"));
                                break;
                            }

                            optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                        }

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.auditLog.setupDescription"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupLogChannel", LanguageService.getByGuild(event.getGuild(), "label.selectChannel"), 1, 1, false, optionList)).queue();
                    }

                    case "logDelete" -> {
                        Webhook webhook = SQLSession.getSqlConnector().getSqlWorker().getLogWebhook(event.getGuild().getId());

                        if (webhook != null) {
                            event.getJDA().retrieveWebhookById(webhook.getChannelId()).queue(webhook1 -> {
                                webhook1.delete().queue();
                                embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.auditLog.deleted"));
                                embedBuilder.setColor(Color.GREEN);
                                event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                                SQLSession.getSqlConnector().getSqlWorker().deleteEntity(webhook);
                            });
                        }
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                        event.editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupLogChannel" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                String value = event.getInteraction().getValues().get(0);

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                if (value.equals("more")) {
                    java.util.List<SelectOption> optionList = new ArrayList<>();

                    for (TextChannel channel : event.getGuild().getTextChannels().stream().skip(24).toList()) {
                        if (optionList.size() == 24) {
                            optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.more"), "more"));
                            break;
                        }

                        optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                    }

                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.auditLog.setupDescription"));

                    event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupLogChannel", LanguageService.getByGuild(event.getGuild(), "label.selectChannel"), 1, 1, false, optionList)).queue();
                    return;
                }


                TextChannel textChannel = event.getGuild().getTextChannelById(value);

                if (textChannel != null) {
                    textChannel.createWebhook("Ree6-Logs").queue(webhook -> {
                        if (SQLSession.getSqlConnector().getSqlWorker().isLogSetup(event.getGuild().getId())) {
                            WebhookUtil.deleteWebhook(event.getGuild().getId(), SQLSession.getSqlConnector().getSqlWorker().getLogWebhook(event.getGuild().getId()));
                        }

                        SQLSession.getSqlConnector().getSqlWorker().setLogWebhook(event.getGuild().getId(), webhook.getId(), webhook.getToken());
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.auditLog.setupSuccess"));
                        embedBuilder.setColor(Color.GREEN);
                        event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    });
                } else {
                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOptionChannel"));
                    event.editMessageEmbeds(embedBuilder.build()).queue();
                }

            }

            case "setupWelcomeMenu" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                java.util.List<SelectOption> optionList = new ArrayList<>();

                switch (event.getInteraction().getValues().get(0)) {

                    case "backToSetupMenu" -> sendDefaultChoice(event);

                    case "welcomeSetup" -> {
                        for (TextChannel channel : event.getGuild().getTextChannels()) {
                            if (optionList.size() == 24) {
                                optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.more"), "more"));
                                break;
                            }

                            optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                        }

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.welcome.setupDescription"));

                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupWelcomeChannel", LanguageService.getByGuild(event.getGuild(), "label.selectChannel"), 1, 1, false, optionList)).queue();
                    }

                    case "welcomeImage" -> {
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.welcome.imageRequired"));
                        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new ArrayList<>()).queue();
                    }

                    case "welcomeDelete" -> {
                        Webhook webhook = SQLSession.getSqlConnector().getSqlWorker().getWelcomeWebhook(event.getGuild().getId());

                        if (webhook != null) {
                            event.getJDA().retrieveWebhookById(webhook.getChannelId()).queue(webhook1 -> {
                                webhook1.delete().queue();
                                embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.welcome.deleted"));
                                embedBuilder.setColor(Color.GREEN);
                                event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                                SQLSession.getSqlConnector().getSqlWorker().deleteEntity(webhook);
                            });
                        }
                    }

                    default -> {
                        if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null)
                            return;

                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                        event.editMessageEmbeds(embedBuilder.build()).queue();
                    }
                }
            }

            case "setupWelcomeChannel" -> {

                if (checkPerms(event.getMember(), event.getChannel())) {
                    return;
                }

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                String value = event.getInteraction().getValues().get(0);

                if (value.equals("more")) {
                    java.util.List<SelectOption> optionList = new ArrayList<>();

                    for (TextChannel channel : event.getGuild().getTextChannels().stream().skip(24).toList()) {
                        if (optionList.size() == 24) {
                            optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.more"), "more"));
                            break;
                        }

                        optionList.add(SelectOption.of(channel.getName(), channel.getId()));
                    }

                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.welcome.setupDescription"));

                    event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupWelcomeChannel", LanguageService.getByGuild(event.getGuild(), "label.selectChannel"), 1, 1, false, optionList)).queue();
                    return;
                }

                TextChannel textChannel = event.getGuild().getTextChannelById(event.getInteraction().getValues().get(0));

                if (textChannel != null) {
                    textChannel.createWebhook("Ree6-Welcome").queue(webhook -> {
                        if (SQLSession.getSqlConnector().getSqlWorker().isWelcomeSetup(event.getGuild().getId())) {
                            WebhookUtil.deleteWebhook(event.getGuild().getId(), SQLSession.getSqlConnector().getSqlWorker().getWelcomeWebhook(event.getGuild().getId()));
                        }

                        SQLSession.getSqlConnector().getSqlWorker().setWelcomeWebhook(event.getGuild().getId(), webhook.getId(), webhook.getToken());
                        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.welcome.setupSuccess"));
                        embedBuilder.setColor(Color.GREEN);
                        event.editMessageEmbeds(embedBuilder.build()).setComponents(new ArrayList<>()).queue();
                    });
                } else {
                    embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOptionChannel"));
                    event.editMessageEmbeds(embedBuilder.build()).queue();
                }

            }

            default -> {
                if (event.getMessage().getEmbeds().isEmpty() || event.getMessage().getEmbeds().get(0) == null) return;

                EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

                embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.default.invalidOption"));
                event.editMessageEmbeds(embedBuilder.build()).queue();
            }
        }
    }

    /**
     * Called when the default choices should be sent.
     *
     * @param event The InteractionEvent of the SelectMenu.
     */
    public void sendDefaultChoice(StringSelectInteractionEvent event) {
        EmbedBuilder embedBuilder = new EmbedBuilder(event.getMessage().getEmbeds().get(0));

        List<SelectOption> optionList = new ArrayList<>();
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.language"), "lang"));
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.auditLog"), "log"));
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.welcomeChannel"), "welcome"));
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.autoRole"), "autorole"));
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.temporalVoice"), "tempvoice"));
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.statistics"), "statistics"));
        optionList.add(SelectOption.of(LanguageService.getByGuild(event.getGuild(), "label.ticketSystem"), "tickets"));

        embedBuilder.setDescription(LanguageService.getByGuild(event.getGuild(), "message.setup.setupMenu"));

        event.editMessageEmbeds(embedBuilder.build()).setActionRow(new StringSelectMenuImpl("setupActionMenu", LanguageService.getByGuild(event.getGuild(), "message.setup.setupMenuPlaceholder"), 1, 1, false, optionList)).queue();
    }

    /**
     * Checks if the user has the required Permissions to use the Command.
     *
     * @param member  The Member who should be checked.
     * @param channel The Channel used.
     * @return True if the user does not have the required Permissions, false if otherwise.
     */
    private boolean checkPerms(Member member, MessageChannel channel) {
        if (member == null || !member.hasPermission(Permission.ADMINISTRATOR)) {
            channel.sendMessage(LanguageService.getByGuild((member == null ? null : member.getGuild()), "message.default.insufficientPermission", Permission.ADMINISTRATOR.name())).queue();
            return true;
        }

        if (!member.getGuild().getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
            channel.sendMessage(LanguageService.getByGuild(member.getGuild(), "message.default.needPermission", Permission.MANAGE_WEBHOOKS.name())).queue();
            return true;
        }

        return false;
    }
}
