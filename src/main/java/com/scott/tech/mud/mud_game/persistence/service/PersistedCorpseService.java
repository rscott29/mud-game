package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.entity.PersistedCorpseEntity;
import com.scott.tech.mud.mud_game.persistence.repository.PersistedCorpseRepository;
import com.scott.tech.mud.mud_game.world.WorldService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class PersistedCorpseService {

    private static final Logger log = LoggerFactory.getLogger(PersistedCorpseService.class);

    private final PersistedCorpseRepository persistedCorpseRepository;
    private final WorldService worldService;
    private final GlobalSettingsRegistry globalSettingsRegistry;

    public PersistedCorpseService(PersistedCorpseRepository persistedCorpseRepository,
                                  WorldService worldService,
                                  GlobalSettingsRegistry globalSettingsRegistry) {
        this.persistedCorpseRepository = persistedCorpseRepository;
        this.worldService = worldService;
        this.globalSettingsRegistry = globalSettingsRegistry;
    }

    @PostConstruct
    void init() {
        restorePersistedCorpses();
    }

    public boolean itemLossEnabled() {
        return globalSettingsRegistry.settings().death().itemLossEnabled();
    }

    public Item createCorpse(Player player, List<Item> contents) {
        if (player == null) {
            throw new IllegalArgumentException("Player is required to create a corpse.");
        }

        return createCorpseItem(
                "corpse_" + player.getId() + "_" + System.currentTimeMillis(),
                player.getName(),
                contents
        );
    }

    public void persistNewCorpse(Room room, Item corpse, String ownerName) {
        if (room == null || corpse == null || ownerName == null || ownerName.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(globalSettingsRegistry.settings().death().corpsePersistenceMinutes() * 60L);
        persistedCorpseRepository.save(new PersistedCorpseEntity(
                corpse.getId(),
                room.getId(),
                ownerName,
                serializeItemIds(corpse.getContainedItems()),
                now,
                expiresAt
        ));
    }

    public void syncCorpse(Room room, Item corpse) {
        if (room == null || corpse == null) {
            return;
        }

        persistedCorpseRepository.findById(corpse.getId()).ifPresent(entity -> {
            if (!corpse.hasContents()) {
                room.removeItem(corpse);
                persistedCorpseRepository.delete(entity);
                return;
            }

            entity.setItemIds(serializeItemIds(corpse.getContainedItems()));
            persistedCorpseRepository.save(entity);
        });
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public void purgeExpiredCorpses() {
        Instant now = Instant.now();
        List<PersistedCorpseEntity> expired = persistedCorpseRepository.findAllByExpiresAtLessThanEqual(now);
        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(entity -> {
            Room room = worldService.getRoom(entity.getRoomId());
            if (room != null) {
                room.getItems().stream()
                        .filter(item -> entity.getCorpseId().equals(item.getId()))
                        .findFirst()
                        .ifPresent(room::removeItem);
            }
        });
        persistedCorpseRepository.deleteAll(expired);
        log.info("Purged {} expired corpse(s)", expired.size());
    }

    void restorePersistedCorpses() {
        purgeExpiredCorpses();

        Instant now = Instant.now();
        List<PersistedCorpseEntity> active = persistedCorpseRepository.findAllByExpiresAtAfterOrderByCreatedAtAsc(now);
        for (PersistedCorpseEntity entity : active) {
            Room room = worldService.getRoom(entity.getRoomId());
            if (room == null) {
                persistedCorpseRepository.delete(entity);
                continue;
            }

            List<Item> contents = deserializeItems(entity.getItemIds());
            room.addItem(createCorpseItem(entity.getCorpseId(), entity.getOwnerName(), contents));
        }
    }

    private Item createCorpseItem(String corpseId, String ownerName, List<Item> contents) {
        List<Item> containedItems = contents == null ? List.of() : List.copyOf(contents);
        String description = containedItems.isEmpty()
                ? Messages.fmt("combat.player_corpse.description.empty", "player", ownerName)
                : Messages.fmt("combat.player_corpse.description.items", "player", ownerName);

        String ownerKey = ownerName.toLowerCase(Locale.ROOT);
        return new Item(
                corpseId,
                Messages.fmt("combat.player_corpse.name", "player", ownerName),
                description,
                List.of(
                        "corpse",
                        "body",
                        "remains",
                        ownerKey,
                        ownerKey + " corpse",
                        ownerKey + " body"
                ),
                false,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                Item.CombatStats.NONE,
                null,
                true,
                containedItems
        );
    }

    private List<Item> deserializeItems(String serializedItemIds) {
        if (serializedItemIds == null || serializedItemIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(serializedItemIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(worldService::getItemById)
                .filter(Objects::nonNull)
                .toList();
    }

    private String serializeItemIds(List<Item> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }

        return items.stream()
                .map(Item::getId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
