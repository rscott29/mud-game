package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;

import java.util.Map;

public record WorldLoadResult(
        Map<String, Room> rooms,
        Map<String, Npc> npcRegistry,
        Map<String, Item> itemRegistry,
        Map<String, String> npcRoomIndex,
        String startRoomId,
        String defaultRecallRoomId
) {
}
