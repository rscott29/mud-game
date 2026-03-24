package com.scott.tech.mud.mud_game.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AmbientEventService {

    private static final Logger log = LoggerFactory.getLogger(AmbientEventService.class);
    private static final String AMBIENT_EVENTS_PATH = "world/ambient-events.json";

    /** Companion dialogue return type with NPC ID for name lookup. */
    public record CompanionLine(String npcId, String message) {}

    private final Map<String, Map<String, List<String>>> zoneEvents = new HashMap<>();
    private final Map<String, Map<String, List<String>>> companionDialogue = new HashMap<>();
    private final Map<String, Double> triggerChances = new HashMap<>();

    private final Map<String, String> lastZoneEvent = new ConcurrentHashMap<>();
    private final Map<String, String> lastCompanionEvent = new ConcurrentHashMap<>();
    private final AiTextPolisher textPolisher;

    private long delayMinMs = 1500;
    private long delayMaxMs = 3500;

  
    public AmbientEventService(ObjectMapper objectMapper, AiTextPolisher textPolisher) {
        this.textPolisher = textPolisher == null ? AiTextPolisher.noOp() : textPolisher;
        try {
            JsonNode root = objectMapper.readTree(
                    new ClassPathResource(AMBIENT_EVENTS_PATH).getInputStream());
            loadZoneEvents(root);
            loadCompanionDialogue(root);
            loadTriggerChances(root);
            loadDelayConfig(root);
            log.info("Loaded ambient events: {} zones, {} companions",
                    zoneEvents.size(), companionDialogue.size());
        } catch (IOException e) {
            log.warn("Could not load ambient events from {}: {}", AMBIENT_EVENTS_PATH, e.getMessage());
        }
    }

    public Optional<String> getRandomAmbientEvent(String zone) {
        String zoneKey = normalizeKey(zone);
        if (zoneKey.isEmpty()) {
            return Optional.empty();
        }

        Map<String, List<String>> events = zoneEvents.get(zoneKey);
        if (events == null || events.isEmpty()) {
            return Optional.empty();
        }

        if (shouldTrigger("uneasy")) {
            List<String> uneasy = events.get("uneasy");
            if (uneasy != null && !uneasy.isEmpty()) {
                return Optional.of(textPolisher.polish(
                        randomFromAvoidRepeat(uneasy, zoneKey + ":uneasy", lastZoneEvent),
                        AiTextPolisher.Style.AMBIENT_EVENT
                ));
            }
        }

        if (shouldTrigger("ambient")) {
            List<String> ambient = events.get("ambient");
            if (ambient != null && !ambient.isEmpty()) {
                return Optional.of(textPolisher.polish(
                        randomFromAvoidRepeat(ambient, zoneKey + ":ambient", lastZoneEvent),
                        AiTextPolisher.Style.AMBIENT_EVENT
                ));
            }
        }

        return Optional.empty();
    }

    public Optional<CompanionLine> getRandomCompanionDialogue(Set<String> followingNpcIds, String zone) {
        String zoneKey = normalizeKey(zone);
        if (zoneKey.isEmpty() || followingNpcIds == null || followingNpcIds.isEmpty()) {
            return Optional.empty();
        }

        if (!shouldTrigger("companionDialogue")) {
            return Optional.empty();
        }

        List<NpcLine> eligible = new ArrayList<>();

        for (String npcId : followingNpcIds) {
            Map<String, List<String>> npcDialogue = companionDialogue.get(npcId);
            if (npcDialogue == null) continue;

            List<String> messages = npcDialogue.get(zoneKey);
            if (messages == null || messages.isEmpty()) continue;

            for (String message : messages) {
                eligible.add(new NpcLine(npcId, message));
            }
        }

        if (eligible.isEmpty()) {
            return Optional.empty();
        }

        NpcLine chosen = randomNpcLineAvoidRepeat(eligible, zoneKey);
        return Optional.of(new CompanionLine(
                chosen.npcId(),
                textPolisher.polish(chosen.message(), AiTextPolisher.Style.NPC_DIALOGUE)
        ));
    }

    public long getRandomDelayMs() {
        return ThreadLocalRandom.current().nextLong(delayMinMs, delayMaxMs + 1);
    }

    public boolean hasAmbientContent(String zone) {
        return zoneEvents.containsKey(normalizeKey(zone));
    }

    private void loadZoneEvents(JsonNode root) {
        JsonNode zones = root.path("zones");
        if (zones.isMissingNode()) return;

        zones.properties().forEach(zoneEntry -> {
            String zoneName = normalizeKey(zoneEntry.getKey());
            JsonNode zoneNode = zoneEntry.getValue();
            Map<String, List<String>> eventsByType = new HashMap<>();

            zoneNode.properties().forEach(typeEntry -> {
                String eventType = typeEntry.getKey();
                List<String> messages = new ArrayList<>();
                typeEntry.getValue().forEach(msg -> messages.add(msg.asText()));
                if (!messages.isEmpty()) {
                    eventsByType.put(eventType, messages);
                }
            });

            if (!eventsByType.isEmpty()) {
                zoneEvents.put(zoneName, eventsByType);
            }
        });
    }

    private void loadCompanionDialogue(JsonNode root) {
        JsonNode dialogueNode = root.path("companionDialogue");
        if (dialogueNode.isMissingNode()) return;

        dialogueNode.properties().forEach(npcEntry -> {
            String npcId = npcEntry.getKey();
            JsonNode npcNode = npcEntry.getValue();
            Map<String, List<String>> zoneDialogue = new HashMap<>();

            npcNode.properties().forEach(zoneEntry -> {
                String zone = normalizeKey(zoneEntry.getKey());
                List<String> messages = new ArrayList<>();
                zoneEntry.getValue().forEach(msg -> messages.add(msg.asText()));
                if (!messages.isEmpty()) {
                    zoneDialogue.put(zone, messages);
                }
            });

            if (!zoneDialogue.isEmpty()) {
                companionDialogue.put(npcId, zoneDialogue);
            }
        });
    }

    private void loadTriggerChances(JsonNode root) {
        JsonNode chances = root.path("triggerChance");
        if (chances.isMissingNode()) return;

        chances.properties().forEach(entry -> {
            double chance = entry.getValue().asDouble();
            if (chance < 0.0 || chance > 1.0) {
                log.warn("Trigger chance '{}' out of range: {}. Clamping.", entry.getKey(), chance);
                chance = Math.max(0.0, Math.min(1.0, chance));
            }
            triggerChances.put(entry.getKey(), chance);
        });
    }

    private void loadDelayConfig(JsonNode root) {
        JsonNode delay = root.path("delayMs");
        if (!delay.isMissingNode()) {
            delayMinMs = delay.path("min").asLong(delayMinMs);
            delayMaxMs = delay.path("max").asLong(delayMaxMs);

            if (delayMinMs > delayMaxMs) {
                log.warn("Ambient delay min {} greater than max {}. Swapping.", delayMinMs, delayMaxMs);
                long temp = delayMinMs;
                delayMinMs = delayMaxMs;
                delayMaxMs = temp;
            }
        }
    }

    private boolean shouldTrigger(String eventType) {
        double chance = triggerChances.getOrDefault(eventType, 0.0);
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private String randomFromAvoidRepeat(List<String> list, String key, Map<String, String> memory) {
        if (list.size() == 1) {
            String only = list.get(0);
            memory.put(key, only);
            return only;
        }

        String last = memory.get(key);
        String chosen;
        int attempts = 0;
        do {
            chosen = list.get(ThreadLocalRandom.current().nextInt(list.size()));
            attempts++;
        } while (chosen.equals(last) && attempts < 10);

        memory.put(key, chosen);
        return chosen;
    }

    private NpcLine randomNpcLineAvoidRepeat(List<NpcLine> lines, String zoneKey) {
        if (lines.size() == 1) {
            NpcLine only = lines.get(0);
            lastCompanionEvent.put(zoneKey, only.message());
            return only;
        }

        String last = lastCompanionEvent.get(zoneKey);
        NpcLine chosen;
        int attempts = 0;
        do {
            chosen = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
            attempts++;
        } while (chosen.message().equals(last) && attempts < 10);

        lastCompanionEvent.put(zoneKey, chosen.message());
        return chosen;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record NpcLine(String npcId, String message) {}
}
