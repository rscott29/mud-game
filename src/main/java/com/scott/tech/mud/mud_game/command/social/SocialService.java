package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

@Service
public class SocialService {

    public CommandResult buildResult(GameSession actorSession,
                                     SocialAction action,
                                     SocialValidationResult validation) {
        String actorName = actorSession.getPlayer().getName();

        return switch (validation.targetMode()) {
            case NONE -> noTargetResult(action, actorName);
            case SELF -> selfTargetResult(action, actorName);
            case PLAYER -> targetedResult(action, actorName, validation.targetSession());
        };
    }

    private CommandResult noTargetResult(SocialAction action, String actorName) {
        String selfMsg = action.format(action.selfMessage(), actorName, null);
        String roomMsg = action.format(action.roomMessage(), actorName, null);

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(roomMsg),
                GameResponse.message(selfMsg)
        );
    }

    private CommandResult selfTargetResult(SocialAction action, String actorName) {
        String selfMsg = "You " + action.name() + " at yourself.";
        String roomMsg = actorName + " " + action.name() + "s at themselves.";

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(roomMsg),
                GameResponse.message(selfMsg)
        );
    }

    private CommandResult targetedResult(SocialAction action, String actorName, GameSession targetSession) {
        String targetName = targetSession.getPlayer().getName();
        String selfMsg = action.format(action.targetedSelf(), actorName, targetName);
        String roomMsg = action.format(action.targetedRoom(), actorName, targetName);
        String targetMsg = action.format(action.targetedTarget(), actorName, targetName);

        return CommandResult.withAction(
                RoomAction.withTarget(roomMsg, targetSession.getSessionId(), targetMsg),
                GameResponse.message(selfMsg)
        );
    }
}
