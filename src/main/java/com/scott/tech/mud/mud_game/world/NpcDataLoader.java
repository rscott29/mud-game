package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Npc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code world/npcs.json}, validates per-NPC config (wander range and combat
 * stats), and produces the immutable NPC registry plus per-NPC give-interaction list.
 *
 * <p>Per-NPC validation that can be done without other registries lives here.
 * Cross-cutting validation (e.g. wander paths referencing rooms, give interactions
 * referencing items) is performed later by {@link WorldValidator}.</p>
 */
@Component
final class NpcDataLoader {

    private static final Logger log = LoggerFactory.getLogger(NpcDataLoader.class);
    private static final String NPCS_FILE = "world/npcs.json";

    private final ObjectMapper objectMapper;

    NpcDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    NpcRegistryLoadResult load() throws Exception {
        NpcData[] npcDataArray = objectMapper.readValue(
                new ClassPathResource(NPCS_FILE).getInputStream(), NpcData[].class);

        Map<String, Npc> map = new HashMap<>();
        Map<String, List<NpcGiveInteraction>> giveInteractions = new HashMap<>();
        for (NpcData n : npcDataArray) {
            long minSec = 0;
            long maxSec = 0;
            List<String> depTemplates = List.of();
            List<String> arrTemplates = List.of();
            List<String> pathList = List.of();

            if (n.getWander() != null) {
                minSec = n.getWander().getMinSeconds();
                maxSec = n.getWander().getMaxSeconds();
                depTemplates = n.getWander().getDepartureTemplates();
                arrTemplates = n.getWander().getArrivalTemplates();
                pathList = n.getWander().getPath();
            }

            validateWanderRange(n.getId(), minSec, maxSec);
            validateCombatConfig(n);

            Npc npc = new Npc(
                    n.getId(), n.getName(), n.getDescription(), n.getKeywords(),
                    n.getPronoun(), n.getPossessive(),
                    minSec, maxSec, depTemplates, arrTemplates, pathList,
                    n.getInteractTemplates(),
                    n.isSentient(), n.getTalkTemplates(), n.getPersonality(), n.isHumorous(),
                    n.isCombatTarget(), n.isRespawns(), n.getMaxHealth(), n.getLevel(), n.getXpReward(),
                    n.getGoldReward(),
                    n.getMinDamage(), n.getMaxDamage(), n.isPlayerDeathEnabled()
            );

            if (map.put(n.getId(), npc) != null) {
                throw new WorldLoadException("Duplicate NPC id: " + n.getId());
            }

            List<NpcGiveInteraction> npcGiveInteractions = n.getGiveInteractions().stream()
                    .map(def -> new NpcGiveInteraction(
                            def.getAcceptedItemIds(),
                            def.getRequiredItemIds(),
                            def.getConsumedItemIds(),
                            def.getRewardItemId(),
                            def.getDenyIfPlayerHasItemId(),
                            def.getGoldCost(),
                            def.getAlreadyOwnedDialogue(),
                            def.getMissingRequiredItemsDialogue(),
                            def.getInsufficientGoldDialogue(),
                            def.getSuccessDialogue(),
                            def.getMissingRewardItemMessage()
                    ))
                    .toList();
            if (!npcGiveInteractions.isEmpty()) {
                giveInteractions.put(n.getId(), npcGiveInteractions);
            }
        }
        log.info("NPC registry loaded: {} npcs", map.size());
        return new NpcRegistryLoadResult(Map.copyOf(map), Map.copyOf(giveInteractions));
    }

    private static void validateWanderRange(String npcId, long minSec, long maxSec) {
        if (minSec < 0 || maxSec < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative wander range");
        }
        if (minSec > 0 && maxSec <= minSec) {
            throw new WorldLoadException(
                    "NPC '" + npcId + "' has invalid wander range: maxSeconds must be greater than minSeconds");
        }
    }

    private static void validateCombatConfig(NpcData npc) {
        String npcId = npc.getId();
        if (!npc.isCombatTarget()) {
            if (npc.getMaxHealth() > 0 || npc.getXpReward() > 0 || npc.getGoldReward() > 0
                    || npc.getMinDamage() > 0 || npc.getMaxDamage() > 0) {
                log.warn("NPC '{}' defines combat stats but combatTarget is false; stats will be ignored", npcId);
            }
            if (npc.getLevel() != 1) {
                log.warn("NPC '{}' defines level={} but combatTarget is false; level is ignored", npcId, npc.getLevel());
            }
            return;
        }

        if (npc.getLevel() < 1) {
            throw new WorldLoadException("NPC '" + npcId + "' has invalid level (must be >= 1)");
        }
        if (npc.getMaxHealth() <= 0) {
            throw new WorldLoadException("NPC '" + npcId + "' is combatTarget but maxHealth <= 0");
        }
        if (npc.getXpReward() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative xpReward");
        }
        if (npc.getGoldReward() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative goldReward");
        }
        if (npc.getMinDamage() < 0 || npc.getMaxDamage() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative damage values");
        }
        if (npc.getMaxDamage() < npc.getMinDamage()) {
            throw new WorldLoadException("NPC '" + npcId + "' has maxDamage lower than minDamage");
        }
    }

    record NpcRegistryLoadResult(
            Map<String, Npc> npcRegistry,
            Map<String, List<NpcGiveInteraction>> npcGiveInteractions
    ) {
    }
}
