package com.scott.tech.mud.mud_game.quest;

import java.util.List;

/**
 * Optional feedback produced by an {@link ObjectiveProgressHandler} when a quest is
 * first started — typically used by DEFEND objectives to push start dialogue, a room
 * announcement, and the per-objective spawn data.
 */
record ObjectiveStartFeedback(
        List<String> playerMessages,
        String roomMessage,
        QuestService.DefendObjectiveStartData defendObjectiveStartData
) {

    static final ObjectiveStartFeedback NONE = new ObjectiveStartFeedback(List.of(), null, null);

    static ObjectiveStartFeedback of(List<String> playerMessages,
                                     String roomMessage,
                                     QuestService.DefendObjectiveStartData defendObjectiveStartData) {
        List<String> safeMessages = playerMessages == null ? List.of() : List.copyOf(playerMessages);
        return new ObjectiveStartFeedback(safeMessages, roomMessage, defendObjectiveStartData);
    }
}
