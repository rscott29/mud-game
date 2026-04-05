package com.scott.tech.mud.mud_game.persistence.cache;

import com.scott.tech.mud.mud_game.consumable.ActiveConsumableEffect;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.PlayerQuestState;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Shared mapper for converting between a live session and cached player state.
 */
public final class PlayerStateSnapshotMapper {

    private PlayerStateSnapshotMapper() {
    }

    public static PlayerStateCache.CachedPlayerState snapshot(GameSession session, Instant cachedAt) {
        if (session == null || session.getPlayer() == null || session.getPlayer().getName() == null) {
            return null;
        }

        Player player = session.getPlayer();
        List<String> followingNpcIds = new ArrayList<>(session.getFollowingNpcs());
        List<ActiveConsumableEffect> activeConsumableEffects = new ArrayList<>(session.getActiveConsumableEffects());

        List<PlayerStateCache.CachedActiveQuest> cachedQuests = player.getQuestState().getActiveQuests().stream()
                .map(activeQuest -> new PlayerStateCache.CachedActiveQuest(
                        activeQuest.getQuestId(),
                        activeQuest.getCurrentObjectiveId(),
                        activeQuest.getObjectiveProgress(),
                        activeQuest.getDialogueStage()))
                .toList();

        return new PlayerStateCache.CachedPlayerState(
                player.getName(),
                player.getCurrentRoomId(),
                player.getLevel(),
                player.getTitle(),
                player.getRace(),
                player.getCharacterClass(),
                player.getPronounsSubject(),
                player.getPronounsObject(),
                player.getPronounsPossessive(),
                player.getDescription(),
                player.getModerationFilters(),
                player.getHealth(),
                player.getMaxHealth(),
                player.getMana(),
                player.getMaxMana(),
                player.getMovement(),
                player.getMaxMovement(),
                player.getExperience(),
                player.getGold(),
                player.getInventory().stream().map(Item::getId).toList(),
                player.getEquippedWeaponId(),
                player.getEquippedItemsSerialized(),
                player.getRecallRoomId(),
                cachedAt,
                cachedQuests,
                new ArrayList<>(player.getQuestState().getCompletedQuests()),
                followingNpcIds,
                activeConsumableEffects
        );
    }

    public static void restore(GameSession session, PlayerStateCache.CachedPlayerState cached) {
        if (session == null || session.getPlayer() == null || cached == null) {
            return;
        }

        Player player = session.getPlayer();
        player.setCurrentRoomId(cached.currentRoomId());
        player.setLevel(cached.level());
        player.setTitle(cached.title());
        player.setRace(cached.race());
        player.setCharacterClass(cached.characterClass());
        player.setPronounsSubject(cached.pronounsSubject());
        player.setPronounsObject(cached.pronounsObject());
        player.setPronounsPossessive(cached.pronounsPossessive());
        player.setDescription(cached.description());
        player.setModerationFilters(cached.moderationFilters());
        player.setHealth(cached.health());
        player.setMaxHealth(cached.maxHealth());
        player.setMana(cached.mana());
        player.setMaxMana(cached.maxMana());
        player.setMovement(cached.movement());
        player.setMaxMovement(cached.maxMovement());
        player.setExperience(cached.experience());
        player.setGold(cached.gold() == null ? 0 : cached.gold());

        if (cached.equippedItems() != null && !cached.equippedItems().isBlank()) {
            player.setEquippedItemsSerialized(cached.equippedItems());
        } else {
            player.setEquippedWeaponId(cached.equippedWeaponId());
        }

        if (cached.recallRoomId() != null && !cached.recallRoomId().isBlank()) {
            player.setRecallRoomId(cached.recallRoomId());
        }

        player.setInventory(resolveInventory(session, cached.inventoryItemIds()));
        player.getQuestState().restore(
                cached.completedQuests() == null ? new HashSet<>() : new HashSet<>(cached.completedQuests()),
                restoreActiveQuests(cached.activeQuests())
        );

        session.clearFollowers();
        session.restoreFollowers(cached.followingNpcIds());
        session.restoreActiveConsumableEffects(cached.activeConsumableEffects());
    }

    private static List<Item> resolveInventory(GameSession session, List<String> inventoryItemIds) {
        if (inventoryItemIds == null || inventoryItemIds.isEmpty()) {
            return List.of();
        }

        return inventoryItemIds.stream()
                .map(id -> session.getWorldService().getItemById(id))
                .filter(Objects::nonNull)
                .toList();
    }

    private static Map<String, PlayerQuestState.ActiveQuest> restoreActiveQuests(
            List<PlayerStateCache.CachedActiveQuest> activeQuests) {
        Map<String, PlayerQuestState.ActiveQuest> restored = new HashMap<>();
        if (activeQuests == null || activeQuests.isEmpty()) {
            return restored;
        }

        for (PlayerStateCache.CachedActiveQuest cachedQuest : activeQuests) {
            if (cachedQuest == null || cachedQuest.questId() == null || cachedQuest.questId().isBlank()) {
                continue;
            }

            PlayerQuestState.ActiveQuest activeQuest = new PlayerQuestState.ActiveQuest(
                    cachedQuest.questId(),
                    cachedQuest.currentObjectiveId()
            );
            for (int i = 0; i < cachedQuest.objectiveProgress(); i++) {
                activeQuest.incrementProgress();
            }
            for (int i = 0; i < cachedQuest.dialogueStage(); i++) {
                activeQuest.advanceDialogue();
            }
            restored.put(cachedQuest.questId(), activeQuest);
        }

        return restored;
    }
}
