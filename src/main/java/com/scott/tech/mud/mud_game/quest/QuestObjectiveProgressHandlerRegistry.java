package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of per-{@link QuestObjectiveType} {@link ObjectiveProgressHandler}
 * implementations. Owns the seven concrete handler classes that previously
 * lived as inner classes of {@link QuestProgressDispatcher}.
 *
 * <p>This is intentionally NOT a Spring bean: it is constructed by the
 * dispatcher and given a {@link Lookup} callback (the dispatcher itself) so
 * that the COLLECT handler can chain into DELIVER without creating a circular
 * dependency at autowiring time.</p>
 */
final class QuestObjectiveProgressHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(QuestObjectiveProgressHandlerRegistry.class);
    private static final ObjectiveProgressHandler NO_OP_HANDLER = new ObjectiveProgressHandler() {};

    /**
     * Callback into the dispatcher for routing decisions a handler cannot make
     * with only its per-objective context.
     */
    interface Lookup {
        Optional<QuestProgressContext> resolveActiveObjective(Player player, String questId);

        ObjectiveProgressHandler handlerFor(QuestObjective objective);
    }

    private final Map<QuestObjectiveType, ObjectiveProgressHandler> handlers;

    QuestObjectiveProgressHandlerRegistry(QuestObjectiveCompleter completer,
                                          WorldService worldService,
                                          DefendObjectiveRuntimeService defendObjectiveRuntimeService,
                                          Lookup lookup) {
        EnumMap<QuestObjectiveType, ObjectiveProgressHandler> map = new EnumMap<>(QuestObjectiveType.class);
        ObjectiveProgressHandler combatHandler = new DefeatObjectiveHandler(completer, worldService, defendObjectiveRuntimeService);

        map.put(QuestObjectiveType.TALK_TO, new TalkToObjectiveHandler(completer));
        map.put(QuestObjectiveType.DELIVER_ITEM, new DeliverItemObjectiveHandler(completer));
        map.put(QuestObjectiveType.COLLECT, new CollectObjectiveHandler(completer, lookup));
        map.put(QuestObjectiveType.DEFEND, combatHandler);
        map.put(QuestObjectiveType.DEFEAT, combatHandler);
        map.put(QuestObjectiveType.VISIT, new VisitObjectiveHandler(completer));
        map.put(QuestObjectiveType.DIALOGUE_CHOICE, new DialogueChoiceObjectiveHandler(completer));
        this.handlers = Collections.unmodifiableMap(map);
    }

    ObjectiveProgressHandler handlerFor(QuestObjective objective) {
        return handlers.getOrDefault(objective.type(), NO_OP_HANDLER);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean playerHasItem(Player player, String itemId) {
        return player.getInventory().stream().anyMatch(item -> item.getId().equals(itemId));
    }

    private static QuestObjective.DialogueData dialogueAtStage(QuestObjective.DialogueData dialogue, int stage) {
        QuestObjective.DialogueData current = dialogue;
        for (int i = 0; i < stage && current != null; i++) {
            current = current.followUp();
        }
        return current;
    }

    // ---------------------------------------------------------------- handler implementations

    private record TalkToObjectiveHandler(QuestObjectiveCompleter completer) implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestService.QuestProgressResult> onTalkToNpc(QuestProgressContext context, Npc npc) {
            if (!npc.getId().equals(context.objective().target())) {
                return Optional.empty();
            }
            return Optional.of(completer.advanceOrComplete(context.player(), context.quest(), context.objective()));
        }
    }

    private record DeliverItemObjectiveHandler(QuestObjectiveCompleter completer) implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestService.QuestProgressResult> onDeliverItem(QuestProgressContext context, Npc npc, Item item) {
            if (!npc.getId().equals(context.objective().target())
                    || !item.getId().equals(context.objective().itemId())) {
                return Optional.empty();
            }

            if (context.objective().consumeItem()) {
                context.player().removeFromInventory(item);
            }

            return Optional.of(completer.advanceOrComplete(context.player(), context.quest(), context.objective()));
        }
    }

    private record CollectObjectiveHandler(QuestObjectiveCompleter completer, Lookup lookup)
            implements ObjectiveProgressHandler {
        @Override
        public ObjectiveStartFeedback onQuestStarted(QuestProgressContext context) {
            if (!playerHasItem(context.player(), context.objective().itemId())) {
                return ObjectiveStartFeedback.NONE;
            }

            log.debug("Player '{}' already has item '{}'; auto-completing COLLECT objective",
                    context.player().getName(), context.objective().itemId());
            completer.advanceOrComplete(context.player(), context.quest(), context.objective());
            return ObjectiveStartFeedback.NONE;
        }

        @Override
        public Optional<QuestService.QuestProgressResult> onCollectItem(QuestProgressContext context, Item item) {
            if (!item.getId().equals(context.objective().itemId())) {
                return Optional.empty();
            }
            return Optional.of(completer.advanceOrComplete(context.player(), context.quest(), context.objective()));
        }

        @Override
        public Optional<QuestService.QuestProgressResult> onDeliverItem(QuestProgressContext context, Npc npc, Item item) {
            if (!item.getId().equals(context.objective().itemId())) {
                return Optional.empty();
            }

            log.debug("Auto-completing COLLECT objective for item '{}' during deliver", item.getId());
            completer.advanceOrComplete(context.player(), context.quest(), context.objective());

            Optional<QuestProgressContext> updated = lookup.resolveActiveObjective(context.player(), context.quest().id());
            if (updated.isPresent() && updated.get().objective().type() == QuestObjectiveType.DELIVER_ITEM) {
                return lookup.handlerFor(updated.get().objective()).onDeliverItem(updated.get(), npc, item);
            }

            return Optional.empty();
        }
    }

    private record DefeatObjectiveHandler(QuestObjectiveCompleter completer,
                                          WorldService worldService,
                                          DefendObjectiveRuntimeService defendObjectiveRuntimeService)
            implements ObjectiveProgressHandler {
        @Override
        public ObjectiveStartFeedback onQuestStarted(QuestProgressContext context) {
            String roomId = Optional.ofNullable(worldService.getNpcRoomId(context.objective().target()))
                    .orElse(context.player().getCurrentRoomId());
            List<Npc> spawnedNpcs = new ArrayList<>();

            for (String npcId : context.objective().spawnNpcs()) {
                Optional<Npc> spawned = worldService.spawnNpcInstance(npcId, roomId);
                if (spawned.isPresent()) {
                    spawnedNpcs.add(spawned.get());
                } else {
                    log.warn("Failed to spawn quest NPC '{}' for quest '{}' objective '{}' in room '{}'",
                            npcId, context.quest().id(), context.objective().id(), roomId);
                }
            }

            if (spawnedNpcs.isEmpty()) {
                return ObjectiveStartFeedback.NONE;
            }

            Npc defendedNpc = worldService.getNpcById(context.objective().target());
            String targetName = defendedNpc != null ? defendedNpc.getName() : context.objective().target();
            String enemyLabel = enemyLabel(spawnedNpcs);
            String attackHint = attackHint(spawnedNpcs.get(0));
            QuestService.DefendObjectiveStartData startData = new QuestService.DefendObjectiveStartData(
                    roomId,
                    targetName,
                    spawnedNpcs.stream().map(Npc::getId).toList(),
                    attackHint,
                    context.objective().targetHealth(),
                    context.objective().timeLimitSeconds(),
                    context.objective().failOnTargetDeath()
            );

            return ObjectiveStartFeedback.of(
                    List.of(Messages.fmt(
                            "quest.defend.start.player",
                            "target", targetName,
                            "enemies", enemyLabel,
                            "attackHint", attackHint
                    )),
                    Messages.fmt(
                            "quest.defend.start.room",
                            "target", targetName,
                            "enemies", enemyLabel
                    ),
                    startData
            );
        }

        @Override
        public Optional<QuestService.QuestProgressResult> onDefeatNpc(QuestProgressContext context, Npc npc) {
            String defeatedNpcTemplateId = Npc.templateIdFor(npc.getId());
            if (!context.objective().spawnNpcs().contains(defeatedNpcTemplateId)) {
                return Optional.empty();
            }

            defendObjectiveRuntimeService.onSpawnedNpcDefeated(context.player(), context.quest().id(), npc);

            int progress = context.state().incrementObjectiveProgress(context.quest().id());
            if (progress >= context.objective().defeatCount()) {
                return Optional.of(completer.advanceOrComplete(context.player(), context.quest(), context.objective()));
            }

            int remaining = context.objective().defeatCount() - progress;
            return Optional.of(QuestService.QuestProgressResult.progress(context.quest(),
                    Messages.fmt("quest.defend.progress", "remaining", String.valueOf(remaining))));
        }

        private static String enemyLabel(List<Npc> spawnedNpcs) {
            if (spawnedNpcs.size() == 1) {
                return spawnedNpcs.get(0).getName();
            }
            return spawnedNpcs.size() + " enemies";
        }

        private static String attackHint(Npc npc) {
            if (npc.getKeywords().isEmpty()) {
                return npc.getName().toLowerCase(Locale.ROOT);
            }
            return npc.getKeywords().get(0);
        }
    }

    private record VisitObjectiveHandler(QuestObjectiveCompleter completer) implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestService.QuestProgressResult> onEnterRoom(QuestProgressContext context, String roomId) {
            if (!roomId.equals(context.objective().target())) {
                return Optional.empty();
            }
            return Optional.of(completer.advanceOrComplete(context.player(), context.quest(), context.objective()));
        }
    }

    private record DialogueChoiceObjectiveHandler(QuestObjectiveCompleter completer) implements ObjectiveProgressHandler {
        @Override
        public QuestService.QuestProgressResult onDialogueChoice(QuestProgressContext context, int choiceIndex) {
            QuestObjective.DialogueData dialogue = context.objective().dialogue();
            if (dialogue == null) {
                return QuestService.QuestProgressResult.failure("No dialogue available.");
            }

            QuestObjective.DialogueData current = dialogueAtStage(dialogue, context.activeQuest().getDialogueStage());
            if (current == null || choiceIndex < 0 || choiceIndex >= current.choices().size()) {
                return QuestService.QuestProgressResult.failure("Invalid choice.");
            }

            QuestObjective.DialogueChoice choice = current.choices().get(choiceIndex);
            if (!choice.correct()) {
                context.state().failQuest(context.quest().id());
                return QuestService.QuestProgressResult.failure(choice.response());
            }

            if (current.followUp() != null) {
                context.state().advanceDialogueStage(context.quest().id());
                return QuestService.QuestProgressResult.dialogue(context.quest(), choice.response(), current.followUp());
            }

            return completer.advanceOrComplete(context.player(), context.quest(), context.objective(), choice.response());
        }
    }
}
