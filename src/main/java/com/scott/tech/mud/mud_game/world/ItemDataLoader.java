package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffect;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectType;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code world/items.json}, validates per-item config (combat stats and
 * consumable effects), and produces the immutable item registry.
 *
 * <p>Cross-cutting validation (e.g. pickup-NPC references) is performed later by
 * {@link WorldValidator}.</p>
 */
@Component
final class ItemDataLoader {

    private static final Logger log = LoggerFactory.getLogger(ItemDataLoader.class);
    private static final String ITEMS_FILE = "world/items.json";

    private final ObjectMapper objectMapper;

    ItemDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Map<String, Item> load() throws Exception {
        ItemData[] itemDataArray = objectMapper.readValue(
                new ClassPathResource(ITEMS_FILE).getInputStream(), ItemData[].class);

        Map<String, Item> map = new HashMap<>();
        for (ItemData i : itemDataArray) {
            List<com.scott.tech.mud.mud_game.model.ItemTrigger> triggers = i.getTriggers().stream()
                    .map(ItemData.TriggerData::toItemTrigger)
                    .filter(t -> t != null)
                    .toList();
            validateItemCombatConfig(i);
            validateItemConsumableConfig(i);
            Item.CombatStats combatStats = toCombatStats(i.getCombatStats());
            EquipmentSlot equipmentSlot = resolveEquipmentSlot(i);
            List<com.scott.tech.mud.mud_game.model.NpcSceneOverride> pickupNpcScenes = i.getPickupNpcScenes().stream()
                    .map(ItemData.PickupNpcSceneData::toNpcSceneOverride)
                    .toList();
            List<ConsumableEffect> consumableEffects = i.getConsumableEffects().stream()
                    .map(ItemData.ConsumableEffectData::toConsumableEffect)
                    .filter(effect -> effect != null)
                    .toList();
            if (map.put(i.getId(), new Item(i.getId(), i.getName(), i.getDescription(), i.getKeywords(),
                    i.isTakeable(), i.getRarity(), i.getRequiredItemIds(), i.getPrerequisiteFailMessage(),
                    triggers, i.getPickupNarrative(), i.getPickupSpawnNpcIds(), pickupNpcScenes,
                    combatStats, equipmentSlot, false, List.of(), consumableEffects)) != null) {
                throw new WorldLoadException("Duplicate item id: " + i.getId());
            }
        }
        log.info("Item registry loaded: {} items", map.size());
        return map;
    }

    private static Item.CombatStats toCombatStats(ItemData.CombatStatsData data) {
        if (data == null) {
            return Item.CombatStats.NONE;
        }
        return new Item.CombatStats(
                data.getMinDamage(),
                data.getMaxDamage(),
                data.getAttackSpeed(),
                data.getHitChance(),
                data.getArmor(),
                data.getAttackVerb()
        );
    }

    private static void validateItemCombatConfig(ItemData item) {
        ItemData.CombatStatsData stats = item.getCombatStats();
        if (stats == null) {
            return;
        }

        String itemId = item.getId();
        if (stats.getMinDamage() < 0 || stats.getMaxDamage() < 0) {
            throw new WorldLoadException("Item '" + itemId + "' has negative damage values");
        }
        if (stats.getMaxDamage() < stats.getMinDamage()) {
            throw new WorldLoadException("Item '" + itemId + "' has maxDamage lower than minDamage");
        }
        if (stats.getHitChance() < -100 || stats.getHitChance() > 100) {
            throw new WorldLoadException("Item '" + itemId + "' has hitChance outside -100..100");
        }
        if (stats.getArmor() < 0) {
            throw new WorldLoadException("Item '" + itemId + "' has negative armor");
        }
        if (stats.getAttackSpeed() < -20 || stats.getAttackSpeed() > 20) {
            throw new WorldLoadException("Item '" + itemId + "' has attackSpeed outside -20..20");
        }
        boolean hasCombatEffect = stats.getMinDamage() > 0
                || stats.getMaxDamage() > 0
                || stats.getHitChance() != 0
                || stats.getAttackSpeed() != 0
                || stats.getArmor() > 0
                || (stats.getAttackVerb() != null && !stats.getAttackVerb().isBlank());
        if (hasCombatEffect && EquipmentSlot.fromString(item.getEquipmentSlot()).isEmpty()) {
            throw new WorldLoadException("Item '" + itemId + "' has combat stats but no equipmentSlot");
        }
    }

    private static void validateItemConsumableConfig(ItemData item) {
        if (item.getConsumableEffects() == null || item.getConsumableEffects().isEmpty()) {
            return;
        }

        String itemId = item.getId();
        for (int i = 0; i < item.getConsumableEffects().size(); i++) {
            ItemData.ConsumableEffectData effect = item.getConsumableEffects().get(i);
            String effectLabel = "Item '" + itemId + "' consumableEffects[" + i + "]";
            ConsumableEffectType type = effect == null ? null : effect.resolveType();
            if (type == null) {
                throw new WorldLoadException(effectLabel + " has unknown type '"
                        + (effect == null ? null : effect.getType()) + "'");
            }
            if (effect.getAmount() <= 0) {
                throw new WorldLoadException(effectLabel + " must define amount > 0");
            }
            boolean hasShoutTemplates = effect.getShoutTemplates() != null && !effect.getShoutTemplates().isEmpty();
            if (type.requiresShoutTemplates() && !hasShoutTemplates) {
                throw new WorldLoadException(effectLabel + " must define at least one shoutTemplate");
            }
            if (!type.allowsShoutTemplates() && hasShoutTemplates) {
                throw new WorldLoadException(effectLabel + " defines shoutTemplates but type does not support them");
            }
            if (!type.isTimed()) {
                if (effect.getDurationSeconds() > 0 || effect.getTickSeconds() > 0) {
                    throw new WorldLoadException(effectLabel + " is instant and cannot define durationSeconds/tickSeconds");
                }
                if (effect.getEndDescription() != null && !effect.getEndDescription().isBlank()) {
                    throw new WorldLoadException(effectLabel + " is instant and cannot define endDescription");
                }
                continue;
            }

            if (effect.getDurationSeconds() <= 0) {
                throw new WorldLoadException(effectLabel + " must define durationSeconds > 0");
            }
            if (effect.getTickSeconds() <= 0) {
                throw new WorldLoadException(effectLabel + " must define tickSeconds > 0");
            }
        }
    }

    private static EquipmentSlot resolveEquipmentSlot(ItemData item) {
        if (item.getEquipmentSlot() == null || item.getEquipmentSlot().isBlank()) {
            return null;
        }

        return EquipmentSlot.fromString(item.getEquipmentSlot())
                .orElseThrow(() -> new WorldLoadException(
                        "Item '" + item.getId() + "' has invalid equipmentSlot '" + item.getEquipmentSlot() + "'"));
    }
}
