package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Pronouns;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

@Service
public class SocialService {

    public CommandResult buildResult(GameSession actorSession,
                                     SocialAction action,
                                     SocialValidationResult validation) {
        Player actor = actorSession.getPlayer();
        String actorName = actor.getName();
        String actorReflexive = actorReflexive(actor);

        return switch (validation.targetMode()) {
            case NONE -> noTargetResult(action, actorName, actorReflexive);
            case SELF -> selfTargetResult(action, actorName, actorReflexive);
            case PLAYER -> targetedResult(action, actorName, actorReflexive, validation.targetSession());
        };
    }

    private CommandResult noTargetResult(SocialAction action, String actorName, String actorReflexive) {
        String selfMsg = action.format(action.selfMessage(), actorName, null, actorReflexive);
        String roomMsg = action.format(action.roomMessage(), actorName, null, actorReflexive);

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(roomMsg, GameResponse.Type.SOCIAL_ACTION),
                GameResponse.socialAction(selfMsg)
        );
    }

    private CommandResult selfTargetResult(SocialAction action, String actorName, String actorReflexive) {
        String selfMsg = action.format(action.selfTargetSelf(), actorName, actorName, actorReflexive);
        String roomMsg = action.format(action.selfTargetRoom(), actorName, actorName, actorReflexive);

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(roomMsg, GameResponse.Type.SOCIAL_ACTION),
                GameResponse.socialAction(selfMsg)
        );
    }

    private CommandResult targetedResult(SocialAction action,
                                         String actorName,
                                         String actorReflexive,
                                         GameSession targetSession) {
        String targetName = targetSession.getPlayer().getName();
        String selfMsg = action.format(action.targetedSelf(), actorName, targetName, actorReflexive);
        String roomMsg = action.format(action.targetedRoom(), actorName, targetName, actorReflexive);
        String targetMsg = action.format(action.targetedTarget(), actorName, targetName, actorReflexive);

        return CommandResult.withAction(
                RoomAction.withTarget(
                        roomMsg,
                        targetSession.getSessionId(),
                        targetMsg,
                        GameResponse.Type.SOCIAL_ACTION
                ),
                GameResponse.socialAction(selfMsg)
        );
    }

    private String actorReflexive(Player actor) {
        Pronouns pronouns = actor.getPronouns();
        if (pronouns == null || pronouns.reflexive() == null || pronouns.reflexive().isBlank()) {
            return "themselves";
        }
        return pronouns.reflexive();
    }
}
