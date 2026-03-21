package com.scott.tech.mud.mud_game.command.bind;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

public class BindRecallCommand implements GameCommand {

    @Override
    public CommandResult execute(GameSession session) {
        Room room = session.getCurrentRoom();
        if (room == null || !room.isRecallBindable()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.bind.unavailable")));
        }

        session.getPlayer().setRecallRoomId(room.getId());
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.bind", "player", session.getPlayer().getName())),
                GameResponse.narrative(Messages.fmt("command.bind.success", "room", room.getName()))
        );
    }
}
