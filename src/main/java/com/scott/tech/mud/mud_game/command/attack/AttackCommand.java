package com.scott.tech.mud.mud_game.command.attack;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestProgressResult;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Initiates or continues combat with an NPC.
 *
 * Usage: attack <target>
 *        kill <target>
 *        fight <target>
 *        hit <target>
 *
 * If already in combat, can use just "attack" to continue fighting current target.
 */
public class AttackCommand implements GameCommand {

    private final String target;
    private final AttackValidator attackValidator;
    private final CombatService combatService;
    private final CombatLoopScheduler combatLoopScheduler;
    private final CombatState combatState;
    private final ExperienceTableService xpTables;
    private final GameSessionManager sessionManager;
    private final PartyService partyService;
    private final WorldBroadcaster broadcaster;
    private final LevelingService levelingService;
    private final WorldService worldService;

    public AttackCommand(String target,
                         AttackValidator attackValidator,
                         CombatService combatService,
                         CombatLoopScheduler combatLoopScheduler,
                         CombatState combatState,
                         ExperienceTableService xpTables,
                         GameSessionManager sessionManager,
                         PartyService partyService,
                         WorldBroadcaster broadcaster,
                         LevelingService levelingService,
                         WorldService worldService) {
        this.target = stripArticle(target);
        this.attackValidator = attackValidator;
        this.combatService = combatService;
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
        AttackValidationResult validation = attackValidator.validate(session, target);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        Npc npc = validation.npc();
        String sessionId = session.getSessionId();
        String playerName = session.getPlayer().getName();
        String roomId = session.getPlayer().getCurrentRoomId();

        List<GameSession> participants = partyService.getPartySessionsInRoom(sessionId, sessionManager, roomId).stream()
                .filter(member -> !combatState.isInCombat(member.getSessionId()) || combatState.isInCombatWith(member.getSessionId(), npc))
                .toList();
        if (participants.isEmpty()) {
            participants = List.of(session);
        }

        // Start or continue combat
        boolean combatStarting = !combatState.isInCombatWith(sessionId, npc);
        CombatEncounter encounter = null;
        for (GameSession participant : participants) {
            encounter = combatState.engage(participant.getSessionId(), npc, roomId);
        }
        if (encounter == null) {
            encounter = combatState.engage(sessionId, npc, roomId);
        }

        // Execute player's attack
        CombatService.AttackResult result = combatService.executePlayerAttack(session, encounter);

        // Build response message
        StringBuilder sb = new StringBuilder();
        if (combatStarting) {
            sb.append(Messages.fmt("combat.begin", "player", playerName, "npc", npc.getName()));
            
            // Warn if unarmed
            if (session.getPlayer().getEquippedWeapon().isEmpty()) {
                sb.append(Messages.get("combat.unarmed_warning"));
            }
            
            sb.append("<br><br>");
        }
        sb.append(result.message());

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

        // Build room action for other players to see
        String actionMsg;
        boolean stillInCombat = !result.targetDefeated();
        if (result.targetDefeated()) {
            actionMsg = participants.size() > 1
                ? Messages.fmt("action.combat.group_defeat",
                    "leader", playerName,
                    "npc", npc.getName())
                : Messages.fmt("action.combat.defeat",
                    "player", playerName,
                    "npc", npc.getName());
        } else {
            for (GameSession participant : participants) {
                combatLoopScheduler.startCombatLoop(participant.getSessionId());
            }
            actionMsg = Messages.fmt("action.combat.attack", 
                    "player", playerName, 
                    "npc", npc.getName());
        }

        appendQuestSummary(sb, result.questProgressResult());
        GameResponse primaryResponse = GameResponse.narrative(sb.toString()).withPlayerStats(session.getPlayer(), xpTables, stillInCombat);
        List<GameResponse> responses = new ArrayList<>(buildQuestProgressResponses(session, result.questProgressResult()));
        if (!responses.isEmpty() && isRoomDisplay(responses.getFirst())) {
            responses.set(0, prependToRoomDisplay(primaryResponse, responses.getFirst()));
        } else {
            responses.addFirst(primaryResponse);
        }

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(actionMsg),
                responses.toArray(new GameResponse[0])
        );
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
                            .withPlayerStats(player, levelingService.getXpTables()));

                    if (xpResult.leveledUp()) {
                        responses.add(GameResponse.narrative(xpResult.levelUpMessage())
                                .withPlayerStats(player, levelingService.getXpTables()));
                    }
                }

                if (result.goldReward() > 0) {
                    responses.add(GameResponse.narrative(
                            Messages.fmt("quest.gold_reward", "gold", String.valueOf(result.goldReward())))
                            .withPlayerStats(player, levelingService.getXpTables()));
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

    /**
     * Strips common leading articles from the target string.
     */
    private static String stripArticle(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();
        for (String article : List.of("a ", "an ", "the ")) {
            if (lower.startsWith(article)) {
                return trimmed.substring(article.length()).trim();
            }
        }
        return trimmed;
    }
}
