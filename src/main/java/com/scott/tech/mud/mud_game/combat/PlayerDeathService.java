package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PersistedCorpseService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class PlayerDeathService {

    private final InventoryService inventoryService;
    private final PlayerRespawnService playerRespawnService;
    private final PersistedCorpseService persistedCorpseService;
    private final PlayerProfileService playerProfileService;
    private final PlayerStateCache stateCache;

    public PlayerDeathService(InventoryService inventoryService,
                              PlayerRespawnService playerRespawnService,
                              PersistedCorpseService persistedCorpseService,
                              PlayerProfileService playerProfileService,
                              PlayerStateCache stateCache) {
        this.inventoryService = inventoryService;
        this.playerRespawnService = playerRespawnService;
        this.persistedCorpseService = persistedCorpseService;
        this.playerProfileService = playerProfileService;
        this.stateCache = stateCache;
    }

    public record DeathOutcome(Room room, Item corpse, List<Item> droppedItems, String promptHtml) {
        public boolean leavesCorpse() {
            return corpse != null;
        }
    }

    public DeathOutcome handleDeath(GameSession session) {
        Player player = session.getPlayer();
        Room deathRoom = session.getCurrentRoom();
        if (deathRoom == null) {
            throw new IllegalStateException("Player " + player.getName() + " died without a current room.");
        }

        player.setHealth(0);

        boolean itemLossEnabled = persistedCorpseService.itemLossEnabled();
        List<Item> droppedItems = new ArrayList<>();
        Item corpse = null;

        if (itemLossEnabled) {
            droppedItems.addAll(player.getInventory());
            for (Item item : droppedItems) {
                player.removeFromInventory(item);
            }

            corpse = persistedCorpseService.createCorpse(player, droppedItems);
            deathRoom.addItem(corpse);
            persistedCorpseService.persistNewCorpse(deathRoom, corpse, player.getName());
        }

        inventoryService.saveInventory(
                player.getName().toLowerCase(Locale.ROOT),
                player.getInventory()
        );
        playerProfileService.saveProfile(player);
        stateCache.cache(session);

        Room respawnRoom = playerRespawnService.previewDestination(session);
        String promptKey = corpse != null
                ? "combat.player_death_prompt"
                : itemLossEnabled
                ? "combat.player_death_prompt_no_corpse"
                : "combat.player_death_prompt_no_item_loss";
        String promptHtml = Messages.fmt(
                promptKey,
                "room", respawnRoom != null ? respawnRoom.getName() : "your recall point"
        );

        return new DeathOutcome(deathRoom, corpse, List.copyOf(droppedItems), promptHtml);
    }
}
