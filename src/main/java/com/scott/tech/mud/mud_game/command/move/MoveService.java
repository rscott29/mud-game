package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.NpcTextRenderer;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.service.AmbientEventService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.service.MovementCostService;
import com.scott.tech.mud.mud_game.service.RoomFlavorScheduler;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MoveService {

    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;
    private final RoomFlavorScheduler roomFlavorScheduler;
    private final LevelingService levelingService;
    private final AmbientEventService ambientEventService;
        private final MoveValidator moveValidator;
        private final MovementCostService movementCostService;
    private final WorldService worldService;
        private final PartyService partyService;
    private final AiTextPolisher textPolisher;
    private final PlayerDeathService playerDeathService;

        public MoveService(WorldBroadcaster worldBroadcaster,
                                           GameSessionManager sessionManager,
                                           RoomFlavorScheduler roomFlavorScheduler,
                                           LevelingService levelingService,
                                           AmbientEventService ambientEventService,
                                           MoveValidator moveValidator,
                                           MovementCostService movementCostService,
                                           WorldService worldService,
                                           PartyService partyService,
                                           AiTextPolisher textPolisher,
                                           PlayerDeathService playerDeathService) {
                this.worldBroadcaster = Objects.requireNonNull(worldBroadcaster, "worldBroadcaster");
                this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
                this.roomFlavorScheduler = Objects.requireNonNull(roomFlavorScheduler, "roomFlavorScheduler");
                this.levelingService = Objects.requireNonNull(levelingService, "levelingService");
                this.ambientEventService = Objects.requireNonNull(ambientEventService, "ambientEventService");
                this.moveValidator = Objects.requireNonNull(moveValidator, "moveValidator");
                this.movementCostService = Objects.requireNonNull(movementCostService, "movementCostService");
                this.worldService = Objects.requireNonNull(worldService, "worldService");
                this.partyService = partyService;
                this.textPolisher = textPolisher == null ? AiTextPolisher.noOp() : textPolisher;
                this.playerDeathService = playerDeathService;
    }

    public CommandResult buildResult(GameSession session,
                                     Direction direction,
                                     MoveValidationResult validation) {
        Room currentRoom = session.getCurrentRoom();
        Room nextRoom = validation.nextRoom();
        String nextRoomId = validation.nextRoomId();

        String wsSessionId = session.getSessionId();
        Player player = session.getPlayer();
        String playerName = player.getName();
        String directionName = direction.name().toLowerCase();

        List<GameResponse> responses = new ArrayList<>();

        // Check for dark room damage (wrong exit)
        if (currentRoom.isDark() && currentRoom.getSafeExit() != null 
                && direction != currentRoom.getSafeExit() && currentRoom.getWrongExitDamage() > 0) {
            int damage = currentRoom.getWrongExitDamage();
            player.setHealth(Math.max(0, player.getHealth() - damage));
            String damageMessage = Messages.fmt("command.move.dark_damage", "damage", String.valueOf(damage));
            if (player.isDead() && playerDeathService != null) {
                PlayerDeathService.DeathOutcome deathOutcome = playerDeathService.handleDeath(session);
                worldBroadcaster.broadcastToRoom(
                        currentRoom.getId(),
                        GameResponse.roomAction(Messages.fmt(
                                deathOutcome.leavesCorpse() ? "combat.player_dies_room" : "combat.player_dies_room.no_corpse",
                                "player", playerName)),
                        wsSessionId
                );
                responses.add(buildDeathRoomRefresh(session,
                        damageMessage
                                + "<br><br>"
                                + Messages.get("combat.player_defeated")
                                + "<br><br>"
                                + deathOutcome.promptHtml()
                ).withPlayerStats(player, levelingService.getXpTables()));
                return CommandResult.of(responses.toArray(new GameResponse[0]));
            }
            responses.add(GameResponse.narrative(damageMessage).withPlayerStats(player, levelingService.getXpTables()));
        }

        worldBroadcaster.broadcastToRoom(
                currentRoom.getId(),
                GameResponse.roomAction(Messages.fmtTemplate(
                        textPolisher.polish(Messages.get("command.move.departure"), AiTextPolisher.Style.ROOM_EVENT),
                        "player", playerName,
                        "direction", directionName)),
                wsSessionId
        );

        player.setResting(false);
        player.setCurrentRoomId(nextRoomId);
                int movementCost = movementCostService.movementCostForMove(player, currentRoom, nextRoom);
                if (movementCost > 0) {
                        player.setMovement(Math.max(0, player.getMovement() - movementCost));
                }

        // Move following NPCs with the player
        moveFollowingNpcs(session, currentRoom, nextRoom, direction);

        String fromDirection = direction.opposite().name().toLowerCase();
        worldBroadcaster.broadcastToRoom(
                nextRoomId,
                GameResponse.roomAction(Messages.fmtTemplate(
                        textPolisher.polish(Messages.get("command.move.arrival"), AiTextPolisher.Style.ROOM_EVENT),
                        "player", playerName,
                        "direction", fromDirection)),
                wsSessionId
        );

        scheduleRoomFlavorMessages(nextRoom, session, playerName, wsSessionId);

        List<String> others = sessionManager.getSessionsInRoom(nextRoomId).stream()
                .filter(s -> !s.getSessionId().equals(wsSessionId))
                .map(s -> s.getPlayer().getName())
                .toList();

        Set<String> inventoryItemIds = player.getInventory().stream()
                .map(Item::getId)
                .collect(java.util.stream.Collectors.toSet());

        GameResponse roomUpdate = GameResponse.roomUpdate(
                nextRoom,
                                buildMovementSuccessMessage(directionName, movementCost),
                others,
                session.getDiscoveredHiddenExits(nextRoom.getId()),
                inventoryItemIds
        );
        ExperienceTableService xpTables = levelingService.getXpTables();
        if (xpTables != null) {
            roomUpdate = roomUpdate.withPlayerStats(player, xpTables);
        }
        responses.add(roomUpdate);

                moveFollowingPartyMembers(session, direction, currentRoom.getId());

        return CommandResult.of(responses.toArray(new GameResponse[0]));
    }

    private String buildMovementSuccessMessage(String directionName, int movementCost) {
        if (movementCost <= 0) {
            return Messages.fmt("command.move.success", "direction", directionName);
        }
        return Messages.fmt(
                "command.move.success_with_cost",
                "direction", directionName,
                "cost", String.valueOf(movementCost)
        );
    }

    private void moveFollowingPartyMembers(GameSession leaderSession, Direction direction, String fromRoomId) {
        if (partyService == null) {
            return;
        }

        List<GameSession> followers = partyService.getFollowersInRoom(
                leaderSession.getSessionId(),
                sessionManager,
                fromRoomId
        );
        if (followers.isEmpty()) {
            return;
        }

        for (GameSession followerSession : followers) {
            MoveValidationResult validation = moveValidator.validate(followerSession, direction);
            if (!validation.allowed()) {
                partyService.leaveGroup(followerSession.getSessionId());
                worldBroadcaster.sendToSession(
                        followerSession.getSessionId(),
                        GameResponse.narrative(Messages.fmt(
                                "command.follow.lost",
                                "player", leaderSession.getPlayer().getName()
                        ))
                );
                worldBroadcaster.broadcastToRoom(
                        fromRoomId,
                        GameResponse.roomAction(Messages.fmt(
                                "action.follow.stop",
                                "player", followerSession.getPlayer().getName()
                        )),
                        followerSession.getSessionId()
                );
                continue;
            }

            CommandResult followMoveResult = buildResult(followerSession, direction, validation);
            List<GameResponse> followResponses = followMoveResult.getResponses();
            for (int index = 0; index < followResponses.size(); index++) {
                GameResponse response = followResponses.get(index);
                if (index == 0 && response.message() != null) {
                    response = response.withAppendedMessage("<br><br>" + Messages.fmt(
                            "command.follow.travel",
                            "player", leaderSession.getPlayer().getName()
                    ));
                }
                worldBroadcaster.sendToSession(followerSession.getSessionId(), response);
            }
        }
    }

    private void moveFollowingNpcs(GameSession session, Room fromRoom, Room toRoom, Direction direction) {
        for (String npcId : session.getFollowingNpcs()) {
            // Find the NPC in the current room
            fromRoom.getNpcs().stream()
                    .filter(npc -> npc.getId().equals(npcId))
                    .findFirst()
                    .ifPresent(npc -> {
                        worldService.moveNpc(npc.getId(), fromRoom.getId(), toRoom.getId());
                        
                        // Broadcast follower movement
                        String dirName = direction.name().toLowerCase();
                        worldBroadcaster.broadcastToRoom(
                                fromRoom.getId(),
                                GameResponse.narrative(Messages.fmtTemplate(
                                        textPolisher.polish(
                                                Messages.get("command.move.follower_departure"),
                                                AiTextPolisher.Style.ROOM_EVENT
                                        ),
                                        "npc", npc.getName(), "direction", dirName)),
                                session.getSessionId()
                        );
                    });
        }
    }

        private GameResponse buildDeathRoomRefresh(GameSession session, String message) {
                Room room = session.getCurrentRoom();
                List<String> others = sessionManager.getSessionsInRoom(room.getId()).stream()
                                .filter(other -> !other.getSessionId().equals(session.getSessionId()))
                                .map(other -> other.getPlayer().getName())
                                .toList();

                return GameResponse.roomRefresh(
                                room,
                                message,
                                others,
                                session.getDiscoveredHiddenExits(room.getId()),
                                Set.of()
                );
        }

    private void scheduleRoomFlavorMessages(Room room, GameSession session, String playerName, String wsSessionId) {
        List<GameResponse> npcInteractions = buildNpcInteractionMessages(room, playerName);
        Collections.shuffle(npcInteractions, ThreadLocalRandom.current());
        long roomEntryActionRevision = session.getActionRevision();

        long nextDelayMs = randomRoomFlavorInitialDelayMs();
        nextDelayMs = scheduleSequentialDirectMessages(
                room.getId(), wsSessionId, npcInteractions, nextDelayMs, roomEntryActionRevision);
        nextDelayMs = scheduleAmbientEvent(room, wsSessionId, nextDelayMs, roomEntryActionRevision);
        scheduleCompanionDialogue(room, session, wsSessionId, nextDelayMs, roomEntryActionRevision);
    }

    private List<GameResponse> buildNpcInteractionMessages(Room room, String playerName) {
        List<GameResponse> messages = new ArrayList<>();

        for (Npc npc : room.getNpcs()) {
            List<String> templates = npc.getInteractTemplates();
            if (templates.isEmpty()) {
                continue;
            }

            String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
            AiTextPolisher.Tone tone = npc.isHumorous()
                    ? AiTextPolisher.Tone.PLAYFUL
                    : AiTextPolisher.Tone.DEFAULT;
            String message = NpcTextRenderer.renderForPlayer(
                    textPolisher.polish(template, AiTextPolisher.Style.ROOM_EVENT, tone),
                    npc,
                    playerName
            );
            messages.add(GameResponse.narrative(message));
        }

        return messages;
    }

    /**
     * Schedules ambient events (cave atmosphere, companion dialogue) if the room has an ambientZone.
     */
    private long scheduleAmbientEvent(Room room, String wsSessionId, long nextDelayMs, long roomEntryActionRevision) {
        String zone = room.getAmbientZone();
        if (zone == null || zone.isBlank()) {
            return nextDelayMs;
        }

        return ambientEventService.getRandomAmbientEvent(zone)
                .map(message -> {
                    scheduleDirectRoomMessage(
                            room.getId(),
                            wsSessionId,
                            GameResponse.ambientEvent(message),
                            nextDelayMs,
                            roomEntryActionRevision
                    );
                    return nextDelayMs + randomRoomFlavorGapMs();
                })
                .orElse(nextDelayMs);
    }

    private void scheduleCompanionDialogue(Room room,
                                           GameSession session,
                                           String wsSessionId,
                                           long nextDelayMs,
                                           long roomEntryActionRevision) {
        String zone = room.getAmbientZone();
        if (zone == null || zone.isBlank()) {
            return;
        }

        if (!session.getFollowingNpcs().isEmpty()) {
            ambientEventService.getRandomCompanionDialogue(session.getFollowingNpcs(), zone).ifPresent(line -> {
                String npcName = resolveNpcName(line.npcId());
                scheduleDirectRoomMessage(
                        room.getId(),
                        wsSessionId,
                        GameResponse.companionDialogue(npcName, line.message()),
                        nextDelayMs,
                        roomEntryActionRevision
                );
            });
        }
    }

    private long scheduleSequentialDirectMessages(String roomId,
                                                  String wsSessionId,
                                                  List<GameResponse> responses,
                                                  long nextDelayMs,
                                                  long roomEntryActionRevision) {
        return roomFlavorScheduler.scheduleSequentialMessages(
                roomId,
                wsSessionId,
                responses,
                nextDelayMs,
                roomEntryActionRevision
        );
    }

    private void scheduleDirectRoomMessage(String roomId,
                                           String wsSessionId,
                                           GameResponse response,
                                           long delayMs,
                                           long roomEntryActionRevision) {
        roomFlavorScheduler.scheduleRoomFlavorMessage(
                roomId,
                wsSessionId,
                response,
                delayMs,
                roomEntryActionRevision
        );
    }

    private long randomRoomFlavorInitialDelayMs() {
        return roomFlavorScheduler.randomInitialDelayMs();
    }

    private long randomRoomFlavorGapMs() {
        return roomFlavorScheduler.randomGapDelayMs();
    }

    private String resolveNpcName(String npcId) {
        Npc npc = worldService.getNpcById(npcId);
        return npc != null ? npc.getName() : "Unknown";
    }
}
