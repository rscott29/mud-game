package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TalkCommand implements GameCommand {

    private final String target;
    private final TalkValidator talkValidator;
    private final TalkService talkService;
    private final QuestService questService;

    public TalkCommand(String target, ChatClient chatClient) {
        this(target, new TalkValidator(), new TalkService(chatClient), null);
    }

    public TalkCommand(String target, TalkValidator talkValidator, TalkService talkService) {
        this(target, talkValidator, talkService, null);
    }

    public TalkCommand(String target, TalkValidator talkValidator, TalkService talkService, 
                       QuestService questService) {
        this.target = target;
        this.talkValidator = talkValidator;
        this.talkService = talkService;
        this.questService = questService;
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

        // Get dialogue text
        String dialogue = talkService.buildDialogue(session, npc);
        
        // Check for quest progress on talking to this NPC
        if (questService != null) {
            var questResult = questService.onTalkToNpc(session.getPlayer(), npc);
            if (questResult.isPresent()) {
                var result = questResult.get();
                String questMessage = result.message();
                if (questMessage != null && !questMessage.isBlank()) {
                    dialogue = dialogue + "<br><br>" + questMessage;
                }
            }
        }
        
        // Wrap in room update so player sees room context
        Room room = session.getCurrentRoom();
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());
        
        GameResponse response = GameResponse.roomUpdate(
                room,
                dialogue,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds);
        
        return CommandResult.withAction(talkAction, response);
    }
}
