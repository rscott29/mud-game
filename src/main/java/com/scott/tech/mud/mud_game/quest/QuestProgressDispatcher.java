package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Routes player events ({@code talk}, {@code deliver}, {@code collect}, {@code defeat},
 * {@code enter room}, {@code dialogue choice}) to the {@link ObjectiveProgressHandler}
 * registered for the player's currently-active objective on each in-flight quest.
 *
 * <p>Slim coordinator: walks the player's active quests, resolves the current
 * objective context, and delegates to the appropriate handler from
 * {@link QuestObjectiveProgressHandlerRegistry}.</p>
 */
@Component
class QuestProgressDispatcher implements QuestObjectiveProgressHandlerRegistry.Lookup {

    private static final Logger log = LoggerFactory.getLogger(QuestProgressDispatcher.class);

    private final QuestRegistry questRegistry;
    private final QuestObjectiveProgressHandlerRegistry handlers;

    QuestProgressDispatcher(QuestRegistry questRegistry,
                            QuestObjectiveCompleter completer,
                            WorldService worldService,
                            DefendObjectiveRuntimeService defendObjectiveRuntimeService) {
        this.questRegistry = questRegistry;
        this.handlers = new QuestObjectiveProgressHandlerRegistry(
                completer, worldService, defendObjectiveRuntimeService, this);
    }

    // ---------------------------------------------------------------- public dispatch API

    Optional<QuestService.QuestProgressResult> onTalkToNpc(Player player, Npc npc) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onTalkToNpc(context, npc));
    }

    Optional<QuestService.QuestProgressResult> onDeliverItem(Player player, Npc npc, Item item) {
        log.debug("Checking quest delivery progress for player='{}', npc='{}', item='{}'",
                player.getName(), npc.getId(), item.getId());
        return processActiveObjectives(player,
                (handler, context) -> handler.onDeliverItem(context, npc, item));
    }

    Optional<QuestService.QuestProgressResult> onCollectItem(Player player, Item item) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onCollectItem(context, item));
    }

    Optional<QuestService.QuestProgressResult> onDefeatNpc(Player player, Npc npc) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onDefeatNpc(context, npc));
    }

    Optional<QuestService.QuestProgressResult> onEnterRoom(Player player, String roomId) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onEnterRoom(context, roomId));
    }

    QuestService.QuestProgressResult onDialogueChoice(Player player, String questId, int choiceIndex) {
        Optional<QuestProgressContext> context = resolveActiveObjective(player, questId);
        if (context.isEmpty()) {
            return QuestService.QuestProgressResult.failure("You are not on that quest.");
        }
        if (context.get().objective().type() != QuestObjectiveType.DIALOGUE_CHOICE) {
            return QuestService.QuestProgressResult.failure("This is not a dialogue choice objective.");
        }
        return handlers.handlerFor(context.get().objective()).onDialogueChoice(context.get(), choiceIndex);
    }

    /**
     * Resolves the start-time feedback (DEFEND spawns, etc.) for the first objective of
     * a freshly-started quest. Returns the *effective* first objective after any
     * auto-completion that {@code onQuestStarted} may have triggered (e.g. COLLECT
     * when the player already has the item).
     */
    StartedQuestSnapshot prepareQuestStart(Player player, String questId) {
        Optional<QuestProgressContext> initial = resolveActiveObjective(player, questId);
        ObjectiveStartFeedback feedback = initial
                .map(context -> handlers.handlerFor(context.objective()).onQuestStarted(context))
                .orElse(ObjectiveStartFeedback.NONE);

        QuestObjective effectiveFirst = resolveActiveObjective(player, questId)
                .map(QuestProgressContext::objective)
                .orElse(null);

        return new StartedQuestSnapshot(feedback, effectiveFirst);
    }

    record StartedQuestSnapshot(ObjectiveStartFeedback feedback, QuestObjective effectiveFirstObjective) {}

    // ---------------------------------------------------------------- Lookup callbacks

    @Override
    public Optional<QuestProgressContext> resolveActiveObjective(Player player, String questId) {
        PlayerQuestState.ActiveQuest active = player.getQuestState().getActiveQuest(questId);
        if (active == null) {
            return Optional.empty();
        }
        return resolveActiveObjective(player, active);
    }

    @Override
    public ObjectiveProgressHandler handlerFor(QuestObjective objective) {
        return handlers.handlerFor(objective);
    }

    // ---------------------------------------------------------------- internals

    private Optional<QuestService.QuestProgressResult> processActiveObjectives(Player player,
                                                                               ObjectiveProgressInvocation invocation) {
        for (PlayerQuestState.ActiveQuest active : player.getQuestState().getActiveQuests()) {
            Optional<QuestProgressContext> context = resolveActiveObjective(player, active);
            if (context.isEmpty()) {
                continue;
            }

            Optional<QuestService.QuestProgressResult> result =
                    invocation.invoke(handlers.handlerFor(context.get().objective()), context.get());
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    private Optional<QuestProgressContext> resolveActiveObjective(Player player, PlayerQuestState.ActiveQuest active) {
        Quest quest = questRegistry.get(active.getQuestId());
        if (quest == null) {
            log.warn("Active quest '{}' could not be resolved", active.getQuestId());
            return Optional.empty();
        }

        QuestObjective objective = quest.getObjective(active.getCurrentObjectiveId());
        if (objective == null) {
            log.warn("Active objective '{}' could not be resolved for quest '{}'",
                    active.getCurrentObjectiveId(), quest.id());
            return Optional.empty();
        }

        return Optional.of(new QuestProgressContext(
                player,
                player.getQuestState(),
                active,
                quest,
                objective
        ));
    }

    @FunctionalInterface
    private interface ObjectiveProgressInvocation {
        Optional<QuestService.QuestProgressResult> invoke(ObjectiveProgressHandler handler, QuestProgressContext context);
    }
}
