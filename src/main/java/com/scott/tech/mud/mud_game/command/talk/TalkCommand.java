package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.ai.chat.client.ChatClient;

public class TalkCommand implements GameCommand {

    private final String target;
    private final TalkValidator talkValidator;
    private final TalkService talkService;

    public TalkCommand(String target, ChatClient chatClient) {
        this(target, new TalkValidator(), new TalkService(chatClient));
    }

    public TalkCommand(String target, TalkValidator talkValidator, TalkService talkService) {
        this.target = target;
        this.talkValidator = talkValidator;
        this.talkService = talkService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        TalkValidationResult validation = talkValidator.validate(session, target);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        Npc npc = validation.npc();
        String playerName = session.getPlayer().getName();
        RoomAction talkAction = RoomAction.inCurrentRoom(
                Messages.fmt("action.talk.npc", "player", playerName, "npc", npc.getName()));

        GameResponse response = talkService.buildResponse(session, npc);
        return CommandResult.withAction(talkAction, response);
    }
}
