package com.scott.tech.mud.mud_game.command.look;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;

public record LookValidationResult(
        boolean allowed,
        LookTargetMode targetMode,
        Npc npc,
        Item item,
        GameSession targetSession,
        GameResponse errorResponse
) {
    public static LookValidationResult room() {
        return new LookValidationResult(true, LookTargetMode.ROOM, null, null, null, null);
    }

    public static LookValidationResult exits() {
        return new LookValidationResult(true, LookTargetMode.EXITS, null, null, null, null);
    }

    public static LookValidationResult npc(Npc npc) {
        return new LookValidationResult(true, LookTargetMode.NPC, npc, null, null, null);
    }

    public static LookValidationResult item(Item item) {
        return new LookValidationResult(true, LookTargetMode.ITEM, null, item, null, null);
    }

    public static LookValidationResult player(GameSession targetSession) {
        return new LookValidationResult(true, LookTargetMode.PLAYER, null, null, targetSession, null);
    }

    public static LookValidationResult deny(GameResponse errorResponse) {
        return new LookValidationResult(false, null, null, null, null, errorResponse);
    }

    public enum LookTargetMode {
        ROOM,
        EXITS,
        NPC,
        ITEM,
        PLAYER
    }
}
