package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.persistence.entity.InventoryItemEntity;
import com.scott.tech.mud.mud_game.persistence.repository.InventoryItemRepository;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Loads and persists a player's inventory to the {@code inventory_items} table.
 *
 * <h3>Lifecycle hooks</h3>
 * <ul>
 *   <li><b>Login / reconnect</b> — {@code LoginHandler} calls {@link #loadInventory} to
 *       restore the player's carried items.</li>
 *   <li><b>Pick-up / drop</b> — {@code PickupCommand} / {@code DropCommand} call
 *       {@link #saveInventory} immediately so changes survive a crash.</li>
 *   <li><b>Disconnect</b> — {@code GameWebSocketHandler.afterConnectionClosed} also
 *       calls {@link #saveInventory} as a safety net.</li>
 * </ul>
 */
@Service
@Transactional
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryItemRepository inventoryItemRepository;

    public InventoryService(InventoryItemRepository inventoryItemRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
    }

    /**
     * Returns the list of items this player is carrying, resolved from the world item registry.
     * Item IDs that no longer exist in the world (e.g. after a data update) are silently dropped.
     */
    @Transactional(readOnly = true)
    public List<Item> loadInventory(String username, WorldService worldService) {
        List<Item> items = inventoryItemRepository.findByUsername(username.toLowerCase())
                .stream()
                .map(e -> worldService.getItemById(e.getItemId()))
                .filter(Objects::nonNull)
                .toList();
        log.debug("Loaded {} inventory item(s) for '{}'", items.size(), username);
        return items;
    }

    /**
     * Replaces the persisted inventory for {@code username} with the given list.
     * Safe to call on every pick-up, drop, or disconnect.
     */
    public void saveInventory(String username, List<Item> items) {
        String key = username.toLowerCase();
        inventoryItemRepository.deleteByUsername(key);
        items.forEach(item -> inventoryItemRepository.save(new InventoryItemEntity(key, item.getId())));
        log.debug("Saved {} inventory item(s) for '{}'", items.size(), key);
    }
}
