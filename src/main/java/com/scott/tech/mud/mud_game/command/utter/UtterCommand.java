package com.scott.tech.mud.mud_game.command.utter;

import com.scott.tech.mud.mud_game.command.attack.AttackValidationResult;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.config.SkillTableService.SkillDefinition;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.CharacterClassNames;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestProgressResult;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UtterCommand implements GameCommand {

    private final List<String> args;
    private final UtterValidator utterValidator;
    private final UtterService utterService;
    private final CombatLoopScheduler combatLoopScheduler;
    private final CombatState combatState;
    private final ExperienceTableService xpTables;
    private final GameSessionManager sessionManager;
    private final PartyService partyService;
    private final WorldBroadcaster broadcaster;
    private final LevelingService levelingService;
    private final WorldService worldService;

    public UtterCommand(List<String> args,
                        UtterValidator utterValidator,
                        UtterService utterService,
                        CombatLoopScheduler combatLoopScheduler,
                        CombatState combatState,
                        ExperienceTableService xpTables,
                        GameSessionManager sessionManager,
                        PartyService partyService,
                        WorldBroadcaster broadcaster,
                        LevelingService levelingService,
                        WorldService worldService) {
        this.args = args == null ? List.of() : List.copyOf(args);
        this.utterValidator = utterValidator;
        this.utterService = utterService;
        this.combatLoopScheduler = combatLoopScheduler;
        this.combatState = combatState;
        this.xpTables = xpTables;
        this.sessionManager = sessionManager;
        this.partyService = partyService;
        this.broadcaster = broadcaster;
        this.levelingService = levelingService;
        this.worldService = worldService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Player player = session.getPlayer();
        if (!CharacterClassNames.isWhisperbinder(player.getCharacterClass())) {
            return CommandResult.of(GameResponse.error(Messages.get("utter.class_required")));
        }

        UtterService.ParsedUtter parsedUtter = utterService.parse(args);
        SkillDefinition utterance = parsedUtter.skill();
        if (!utterService.isUnlocked(player, utterance)) {
            return CommandResult.of(GameResponse.error(Messages.fmt(
                    "utter.unavailable",
                "utterance", utterance.name()
            )));
        }

        int manaCost = utterService.manaCost(utterance);
        if (player.getMana() < manaCost) {
            return CommandResult.of(GameResponse.error(Messages.fmt(
                    "utter.not_enough_mana",
                "utterance", utterance.name(),
                    "mana", String.valueOf(manaCost)
            )));
        }

        String target = parsedUtter.target();
        AttackValidationResult validation = utterValidator.validate(session, target);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        Npc npc = validation.npc();
        String sessionId = session.getSessionId();
        String playerName = player.getName();
        String roomId = player.getCurrentRoomId();

        List<GameSession> participants = partyService.getPartySessionsInRoom(sessionId, sessionManager, roomId).stream()
                .filter(member -> !combatState.isInCombat(member.getSessionId())
                        || combatState.isInCombatWith(member.getSessionId(), npc))
                .toList();
        if (participants.isEmpty()) {
            participants = List.of(session);
        }

        boolean combatStarting = !combatState.isInCombatWith(sessionId, npc);
        CombatEncounter encounter = null;
        for (GameSession participant : participants) {
            encounter = combatState.engage(participant.getSessionId(), npc, roomId);
        }
        if (encounter == null) {
            encounter = combatState.engage(sessionId, npc, roomId);
        }

        CombatService.AttackResult result = utterService.cast(session, encounter, utterance);

        StringBuilder narrative = new StringBuilder();
        if (combatStarting) {
            narrative.append(Messages.fmt("combat.begin", "player", playerName, "npc", npc.getName()))
                    .append("<br><br>");
        }
        narrative.append(result.message());

        for (GameSession participant : participants) {
            if (participant.getSessionId().equals(sessionId)) {
                continue;
            }
            if (combatStarting) {
                broadcaster.sendToSession(
                        participant.getSessionId(),
                        GameResponse.narrative(Messages.fmt("combat.begin", "player", playerName, "npc", npc.getName()))
                );
            }
            broadcaster.sendToSession(participant.getSessionId(), GameResponse.narrative(result.partyMessage()));
        }

        String actionMessage;
        if (result.targetDefeated()) {
            actionMessage = participants.size() > 1
                    ? Messages.fmt("action.combat.group_defeat", "leader", playerName, "npc", npc.getName())
                    : Messages.fmt("action.combat.defeat", "player", playerName, "npc", npc.getName());
        } else {
            for (GameSession participant : participants) {
                combatLoopScheduler.startCombatLoop(participant.getSessionId());
            }
            actionMessage = Messages.fmt("action.combat.utter", "player", playerName, "npc", npc.getName());
        }

        LevelingService.XpGainResult xpResult = null;
        if (result.xpGained() > 0) {
            xpResult = levelingService.addExperience(player, result.xpGained());
        }

        appendQuestSummary(narrative, result.questProgressResult());
        GameResponse primaryResponse = withCurrentStats(GameResponse.narrative(narrative.toString()), session);
        List<GameResponse> responses = new ArrayList<>(buildQuestProgressResponses(session, result.questProgressResult()));
        appendLevelingResponses(session, responses, xpResult);
        if (!responses.isEmpty() && isRoomDisplay(responses.getFirst())) {
            responses.set(0, prependToRoomDisplay(primaryResponse, responses.getFirst()));
        } else {
            responses.addFirst(primaryResponse);
        }

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(actionMessage),
                responses.toArray(new GameResponse[0])
        );
    }

    private void appendLevelingResponses(GameSession session,
                                         List<GameResponse> responses,
                                         LevelingService.XpGainResult xpResult) {
        if (xpResult == null) {
            return;
        }

        Player player = session.getPlayer();

        if (xpResult.leveledUp()) {
            responses.add(withCurrentStats(GameResponse.narrative(xpResult.levelUpMessage()), session));
        }

        for (String skillName : levelingService.getNewlyUnlockedSkillNames(
                player,
                xpResult.oldLevel(),
                xpResult.newLevel()
        )) {
            responses.add(GameResponse.narrative(Messages.fmt("skill.unlock", "skill", skillName)));
        }

        if (xpResult.leveledUp()) {
            broadcaster.broadcastToAll(GameResponse.narrative(Messages.fmt(
                    "level.up.world",
                    "name", player.getName(),
                    "level", String.valueOf(xpResult.newLevel())
            )));
        }
    }

    private boolean isRoomDisplay(GameResponse response) {
        if (response == null || response.type() == null) {
            return false;
        }

        return response.type() == GameResponse.Type.WELCOME
                || response.type() == GameResponse.Type.ROOM_UPDATE
                || response.type() == GameResponse.Type.ROOM_REFRESH;
    }

    private GameResponse prependToRoomDisplay(GameResponse primaryResponse, GameResponse roomDisplay) {
        return new GameResponse(
                roomDisplay.type(),
                joinMessages(primaryResponse.message(), roomDisplay.message()),
                roomDisplay.room(),
                roomDisplay.mask() || primaryResponse.mask(),
                primaryResponse.from() != null ? primaryResponse.from() : roomDisplay.from(),
                primaryResponse.token() != null ? primaryResponse.token() : roomDisplay.token(),
                roomDisplay.inventory() != null ? roomDisplay.inventory() : primaryResponse.inventory(),
                roomDisplay.whoPlayers() != null ? roomDisplay.whoPlayers() : primaryResponse.whoPlayers(),
                primaryResponse.playerStats() != null ? primaryResponse.playerStats() : roomDisplay.playerStats(),
                primaryResponse.combatStats() != null ? primaryResponse.combatStats() : roomDisplay.combatStats(),
                primaryResponse.characterCreation() != null ? primaryResponse.characterCreation() : roomDisplay.characterCreation()
        );
    }

    private String joinMessages(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        return first + "<br><br>" + second;
    }

    private void appendQuestSummary(StringBuilder combatNarrative, QuestProgressResult result) {
        if (combatNarrative == null || result == null) {
            return;
        }

        switch (result.type()) {
            case OBJECTIVE_COMPLETE -> {
                if (result.message() != null && !result.message().isBlank()) {
                    combatNarrative.append("<br><br>").append(result.message());
                }
                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null && !effects.dialogue().isEmpty()) {
                    combatNarrative.append("<br><br>").append(String.join("<br>", effects.dialogue()));
                }
            }
            default -> {
            }
        }
    }

    private List<GameResponse> buildQuestProgressResponses(GameSession session, QuestProgressResult result) {
        if (result == null) {
            return List.of();
        }

        List<GameResponse> responses = new ArrayList<>();
        List<String> narrative = new ArrayList<>();
        Player player = session.getPlayer();
        Room currentRoom = session.getCurrentRoom();
        boolean inventoryModified = false;
        boolean roomStateChanged = false;

        switch (result.type()) {
            case PROGRESS -> {
                return List.of();
            }
            case OBJECTIVE_COMPLETE -> {
                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    narrative.addAll(effects.dialogue());

                    if (effects.startFollowing() != null) {
                        session.addFollower(effects.startFollowing());
                    }
                    if (effects.stopFollowing() != null) {
                        session.removeFollower(effects.stopFollowing());
                    }

                    for (String itemId : effects.addItems()) {
                        Item item = worldService.getItemById(itemId);
                        if (item != null) {
                            player.addToInventory(item);
                            inventoryModified = true;
                        }
                    }
                }
            }
            case QUEST_COMPLETE -> {
                narrative.addAll(result.messages());
                inventoryModified = !result.rewardItems().isEmpty();

                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    narrative.addAll(effects.dialogue());

                    if (effects.startFollowing() != null) {
                        session.addFollower(effects.startFollowing());
                    }
                    if (effects.stopFollowing() != null) {
                        session.removeFollower(effects.stopFollowing());
                    }

                    for (String itemId : effects.addItems()) {
                        Item item = worldService.getItemById(itemId);
                        if (item != null) {
                            player.addToInventory(item);
                            inventoryModified = true;
                        }
                    }
                }

                if (result.effects() != null) {
                    if (result.effects().revealHiddenExit() != null) {
                        QuestCompletionEffects.HiddenExitReveal reveal = result.effects().revealHiddenExit();
                        session.discoverExit(reveal.roomId(), reveal.direction());
                        roomStateChanged = true;
                    }
                    if (result.effects().npcDescriptionUpdates() != null) {
                        for (QuestCompletionEffects.NpcDescriptionUpdate update : result.effects().npcDescriptionUpdates()) {
                            worldService.updateNpcDescription(update.npcId(), update.newDescription());
                            roomStateChanged = true;
                        }
                    }
                }

                responses.add(GameResponse.narrative(
                        Messages.fmt("quest.completed", "quest", result.quest().name())));

                if (result.xpReward() > 0) {
                    LevelingService.XpGainResult xpResult = levelingService.addExperience(player, result.xpReward());
                    responses.add(GameResponse.narrative(
                                    Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())))
                            .withPlayerStats(player, xpTables, combatState.getEncounter(session.getSessionId()).orElse(null)));

                    if (xpResult.leveledUp()) {
                        responses.add(GameResponse.narrative(xpResult.levelUpMessage())
                                .withPlayerStats(player, xpTables, combatState.getEncounter(session.getSessionId()).orElse(null)));
                    }
                }

                if (result.goldReward() > 0) {
                    responses.add(GameResponse.narrative(
                                    Messages.fmt("quest.gold_reward", "gold", String.valueOf(result.goldReward())))
                            .withPlayerStats(player, xpTables, combatState.getEncounter(session.getSessionId()).orElse(null)));
                }

                for (Item item : result.rewardItems()) {
                    responses.add(GameResponse.narrative(
                            Messages.fmt("quest.item_reward", "item", item.getName())));
                }
            }
            default -> {
                if (result.message() != null && !result.message().isBlank()) {
                    narrative.add(result.message());
                }
            }
        }

        if (!narrative.isEmpty() || inventoryModified || roomStateChanged) {
            String narrativeHtml = narrative.isEmpty() ? "" : "<br><br>" + String.join("<br>", narrative);
            if (currentRoom != null) {
                Set<String> inventoryItemIds = player.getInventory().stream()
                        .map(Item::getId)
                        .collect(Collectors.toSet());
                responses.addFirst(GameResponse.roomUpdate(
                        currentRoom,
                        narrativeHtml,
                        List.of(),
                        session.getDiscoveredHiddenExits(currentRoom.getId()),
                        inventoryItemIds
                ));
            } else if (!narrativeHtml.isBlank()) {
                responses.addFirst(GameResponse.narrative(narrativeHtml));
            }
        }

        return responses;
    }

    private GameResponse withCurrentStats(GameResponse response, GameSession session) {
        if (response == null || session == null) {
            return response;
        }

        return response.withPlayerStats(
                session.getPlayer(),
            xpTables,
                combatState.getEncounter(session.getSessionId()).orElse(null)
        );
    }

}