package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestPresentation;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestProgressResult;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TalkCommand implements GameCommand {

    private final String target;
    private final TalkValidator talkValidator;
    private final TalkService talkService;
    private final QuestService questService;
    private final LevelingService levelingService;
    private final WorldService worldService;

    public TalkCommand(String target, ChatClient chatClient) {
        this(target, new TalkValidator(), new TalkService(chatClient), null, null, null);
    }

    public TalkCommand(String target, TalkValidator talkValidator, TalkService talkService) {
        this(target, talkValidator, talkService, null, null, null);
    }

    public TalkCommand(String target, TalkValidator talkValidator, TalkService talkService,
                       QuestService questService) {
        this(target, talkValidator, talkService, questService, null, null);
    }

    public TalkCommand(String target, TalkValidator talkValidator, TalkService talkService,
                       QuestService questService, LevelingService levelingService, WorldService worldService) {
        this.target = target;
        this.talkValidator = talkValidator;
        this.talkService = talkService;
        this.questService = questService;
        this.levelingService = levelingService;
        this.worldService = worldService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        TalkValidationResult validation = talkValidator.validate(session, target);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        Npc npc = validation.npc();
        Player player = session.getPlayer();
        session.setLastTalkedNpcId(npc.getId());
        RoomAction talkAction = RoomAction.inCurrentRoom(
                Messages.fmt("action.talk.npc", "player", player.getName(), "npc", npc.getName()));

        String dialogue = talkService.buildDialogue(session, npc);
        QuestOutput questOutput = QuestOutput.NONE;

        if (questService != null) {
            Optional<QuestProgressResult> questResult = questService.onTalkToNpc(player, npc);
            if (questResult.isPresent()) {
                questOutput = buildQuestOutput(session, questResult.get());
                if (!questOutput.narrative().isEmpty()) {
                    dialogue = dialogue + "<br><br>" + String.join("<br>", questOutput.narrative());
                }
            }

            List<Quest> availableQuests = questService.getAvailableQuestsForNpc(player, npc.getId());
            if (!availableQuests.isEmpty()) {
                dialogue = dialogue + "<br><br>" + buildQuestOffer(npc, availableQuests, player.getLevel());
            }
        }

        return buildResponse(session, talkAction, dialogue, questOutput.notifications());
    }

    private CommandResult buildResponse(GameSession session,
                                        RoomAction talkAction,
                                        String dialogue,
                                        List<GameResponse> extraResponses) {
        Room room = session.getCurrentRoom();
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        GameResponse roomResponse = GameResponse.roomUpdate(
                room,
                dialogue,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds);

        if (extraResponses.isEmpty()) {
            return CommandResult.withAction(talkAction, roomResponse);
        }

        List<GameResponse> responses = new ArrayList<>();
        responses.add(roomResponse);
        responses.addAll(extraResponses);
        return CommandResult.withAction(talkAction, responses.toArray(new GameResponse[0]));
    }

    private String buildQuestOffer(Npc npc, List<Quest> availableQuests, int playerLevel) {
        List<String> lines = new ArrayList<>();
        lines.add("<div class='quest-available'><strong>" + npc.getName() + " has quest"
                + (availableQuests.size() == 1 ? "" : "s") + " for you.</strong></div>");

        if (availableQuests.size() == 1) {
            Quest quest = availableQuests.getFirst();
            lines.add("<div class='quest-offer'><strong>" + quest.name() + "</strong><br><small>"
                    + quest.description() + "</small>"
                    + QuestPresentation.buildMetaBadges(quest, playerLevel)
                    + "</div>");
            lines.add("<div class='quest-hint'>Type <strong>accept</strong> now, or <strong>accept "
                    + npc.getName().toLowerCase(Locale.ROOT) + "</strong>.</div>");
            return String.join("", lines);
        }

        for (Quest quest : availableQuests) {
            lines.add("<div class='quest-offer'><strong>" + quest.name() + "</strong><br><small>"
                    + quest.description() + "</small>"
                    + QuestPresentation.buildMetaBadges(quest, playerLevel)
                    + "</div>");
        }
        lines.add("<div class='quest-hint'>Type <strong>accept " + npc.getName().toLowerCase(Locale.ROOT)
                + "</strong> to list them, or <strong>accept [quest name]</strong> to choose one.</div>");
        return String.join("", lines);
    }

    private QuestOutput buildQuestOutput(GameSession session, QuestProgressResult result) {
        List<String> narrative = new ArrayList<>();
        List<GameResponse> notifications = new ArrayList<>();
        Player player = session.getPlayer();

        switch (result.type()) {
            case OBJECTIVE_COMPLETE -> {
                if (result.message() != null && !result.message().isBlank()) {
                    narrative.add(result.message());
                }

                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    narrative.addAll(effects.dialogue());

                    if (effects.startFollowing() != null) {
                        session.addFollower(effects.startFollowing());
                    }
                    if (effects.stopFollowing() != null) {
                        session.removeFollower(effects.stopFollowing());
                    }

                    if (worldService != null) {
                        for (String itemId : effects.addItems()) {
                            Item item = worldService.getItemById(itemId);
                            if (item != null) {
                                player.addToInventory(item);
                            }
                        }
                    }
                }
            }
            case QUEST_COMPLETE -> {
                narrative.addAll(result.messages());

                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    narrative.addAll(effects.dialogue());

                    if (effects.startFollowing() != null) {
                        session.addFollower(effects.startFollowing());
                    }
                    if (effects.stopFollowing() != null) {
                        session.removeFollower(effects.stopFollowing());
                    }

                    if (worldService != null) {
                        for (String itemId : effects.addItems()) {
                            Item item = worldService.getItemById(itemId);
                            if (item != null) {
                                player.addToInventory(item);
                            }
                        }
                    }
                }

                if (result.effects() != null) {
                    if (result.effects().revealHiddenExit() != null) {
                        QuestCompletionEffects.HiddenExitReveal reveal = result.effects().revealHiddenExit();
                        session.discoverExit(reveal.roomId(), reveal.direction());
                    }
                    if (worldService != null && result.effects().npcDescriptionUpdates() != null) {
                        for (QuestCompletionEffects.NpcDescriptionUpdate update : result.effects().npcDescriptionUpdates()) {
                            worldService.updateNpcDescription(update.npcId(), update.newDescription());
                        }
                    }
                }

                notifications.add(GameResponse.narrative(
                        Messages.fmt("quest.completed", "quest", result.quest().name())));

                if (result.xpReward() > 0 && levelingService != null) {
                    LevelingService.XpGainResult xpResult = levelingService.addExperience(player, result.xpReward());
                    notifications.add(GameResponse.narrative(
                                    Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())))
                            .withPlayerStats(player, levelingService.getXpTables()));

                    if (xpResult.leveledUp()) {
                        notifications.add(GameResponse.narrative(xpResult.levelUpMessage())
                                .withPlayerStats(player, levelingService.getXpTables()));
                    }
                }

                if (result.goldReward() > 0) {
                    GameResponse goldResponse = GameResponse.narrative(
                            Messages.fmt("quest.gold_reward", "gold", String.valueOf(result.goldReward())));
                    if (levelingService != null) {
                        goldResponse = goldResponse.withPlayerStats(player, levelingService.getXpTables());
                    }
                    notifications.add(goldResponse);
                }

                for (Item item : result.rewardItems()) {
                    notifications.add(GameResponse.narrative(
                            Messages.fmt("quest.item_reward", "item", item.getName())));
                }
            }
            default -> {
                if (result.message() != null && !result.message().isBlank()) {
                    narrative.add(result.message());
                }
            }
        }

        return new QuestOutput(narrative, notifications);
    }

    private record QuestOutput(List<String> narrative, List<GameResponse> notifications) {
        private static final QuestOutput NONE = new QuestOutput(List.of(), List.of());
    }
}
