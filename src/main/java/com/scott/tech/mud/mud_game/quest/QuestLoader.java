package com.scott.tech.mud.mud_game.quest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads quest definitions from quests.json.
 */
@Component
public class QuestLoader {

    private static final Logger log = LoggerFactory.getLogger(QuestLoader.class);
    private static final String QUESTS_FILE = "world/quests.json";

    private final ObjectMapper objectMapper;

    public QuestLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Loads and validates all quests from quests.json.
     * @return Map of quest ID to Quest definition
     */
    public Map<String, Quest> load() throws Exception {
        QuestData[] questDefs = objectMapper.readValue(
                new ClassPathResource(QUESTS_FILE).getInputStream(),
                QuestData[].class
        );

        Map<String, Quest> quests = new HashMap<>();
        List<String> errors = new ArrayList<>();

        for (QuestData def : questDefs) {
            try {
                Quest quest = buildQuest(def);
                if (quests.put(quest.id(), quest) != null) {
                    errors.add("Duplicate quest id: " + quest.id());
                }
            } catch (Exception e) {
                errors.add("Failed to load quest '" + def.id + "': " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new WorldLoadException("Quest loading failed:\n - " + String.join("\n - ", errors));
        }

        log.info("Loaded {} quests from {}", quests.size(), QUESTS_FILE);
        return quests;
    }

    private Quest buildQuest(QuestData def) {
        List<QuestObjective> objectives = new ArrayList<>();
        if (def.objectives != null) {
            for (ObjectiveData objDef : def.objectives) {
                objectives.add(buildObjective(objDef));
            }
        }

        QuestPrerequisites prereqs = QuestPrerequisites.NONE;
        if (def.prerequisites != null) {
            prereqs = new QuestPrerequisites(
                    def.prerequisites.minLevel,
                    def.prerequisites.completedQuests,
                    def.prerequisites.requiredItems
            );
        }

        QuestRewards rewards = QuestRewards.NONE;
        if (def.rewards != null) {
            rewards = new QuestRewards(def.rewards.items, def.rewards.xp);
        }

        QuestCompletionEffects effects = QuestCompletionEffects.NONE;
        if (def.completionEffects != null) {
            QuestCompletionEffects.HiddenExitReveal exitReveal = null;
            if (def.completionEffects.revealHiddenExit != null) {
                Direction dir = Direction.fromString(def.completionEffects.revealHiddenExit.direction);
                if (dir != null) {
                    exitReveal = new QuestCompletionEffects.HiddenExitReveal(
                            def.completionEffects.revealHiddenExit.roomId,
                            dir
                    );
                }
            }

            List<QuestCompletionEffects.NpcDescriptionUpdate> npcUpdates = new ArrayList<>();
            if (def.completionEffects.updateNpcDescriptions != null) {
                for (NpcDescriptionUpdateData update : def.completionEffects.updateNpcDescriptions) {
                    npcUpdates.add(new QuestCompletionEffects.NpcDescriptionUpdate(
                            update.npcId,
                            update.newDescription,
                            update.originalDescription
                    ));
                }
            }

            List<QuestCompletionEffects.HiddenExitReveal> resetExits = new ArrayList<>();
            if (def.completionEffects.resetDiscoveredExits != null) {
                for (HiddenExitData exitData : def.completionEffects.resetDiscoveredExits) {
                    Direction resetDir = Direction.fromString(exitData.direction);
                    if (resetDir != null) {
                        resetExits.add(new QuestCompletionEffects.HiddenExitReveal(exitData.roomId, resetDir));
                    }
                }
            }

            if (exitReveal != null || !npcUpdates.isEmpty() || !resetExits.isEmpty()) {
                effects = new QuestCompletionEffects(exitReveal, npcUpdates, resetExits);
            }
        }

        return new Quest(
                def.id,
                def.name,
                def.description,
                def.giver,
                def.startDialogue,
                prereqs,
                objectives,
                rewards,
                def.completionDialogue,
                effects
        );
    }

    private QuestObjective buildObjective(ObjectiveData def) {
        QuestObjectiveType type = QuestObjectiveType.valueOf(def.type);

        QuestObjective.DialogueData dialogue = null;
        if (def.dialogue != null) {
            dialogue = buildDialogue(def.dialogue);
        }

        ObjectiveEffects effects = ObjectiveEffects.NONE;
        if (def.onComplete != null) {
            ObjectiveEffects.RelocateItem relocate = null;
            if (def.onComplete.relocateItem != null) {
                relocate = new ObjectiveEffects.RelocateItem(
                        def.onComplete.relocateItem.itemId,
                        def.onComplete.relocateItem.targetRooms
                );
            }
            effects = new ObjectiveEffects(
                    relocate, 
                    def.onComplete.startFollowing,
                    def.onComplete.stopFollowing,
                    def.onComplete.addItems,
                    def.onComplete.dialogue);
        }

        return new QuestObjective(
                def.id,
                type,
                def.description,
                def.target,
                def.itemId,
                def.consumeItem,
                def.spawnNpcs,
                def.defeatCount,
                def.failOnTargetDeath,
                dialogue,
                def.requiresPrevious,
                effects
        );
    }

    private QuestObjective.DialogueData buildDialogue(DialogueChoiceData def) {
        List<QuestObjective.DialogueChoice> choices = new ArrayList<>();
        if (def.choices != null) {
            for (ChoiceData choice : def.choices) {
                choices.add(new QuestObjective.DialogueChoice(
                        choice.text,
                        choice.correct,
                        choice.response
                ));
            }
        }

        QuestObjective.DialogueData followUp = null;
        if (def.followUp != null) {
            followUp = buildDialogue(def.followUp);
        }

        return new QuestObjective.DialogueData(def.question, choices, followUp);
    }

    // ----- JSON data classes -----

    static class QuestData {
        public String id;
        public String name;
        public String description;
        public String giver;
        public List<String> startDialogue;
        public PrerequisitesData prerequisites;
        public List<ObjectiveData> objectives;
        public RewardsData rewards;
        public List<String> completionDialogue;
        public CompletionEffectsData completionEffects;
    }

    static class PrerequisitesData {
        public int minLevel = 1;
        public List<String> completedQuests = List.of();
        public List<String> requiredItems = List.of();
    }

    static class ObjectiveData {
        public String id;
        public String type;
        public String description;
        public String target;
        public String itemId;
        public boolean consumeItem = false;
        public List<String> spawnNpcs = List.of();
        public int defeatCount = 0;
        public boolean failOnTargetDeath = false;
        public DialogueChoiceData dialogue;
        public boolean requiresPrevious = false;
        public ObjectiveEffectsData onComplete;
    }

    static class ObjectiveEffectsData {
        public RelocateItemData relocateItem;
        public String startFollowing;
        public String stopFollowing;
        public List<String> addItems = List.of();
        public List<String> dialogue = List.of();
    }

    static class RelocateItemData {
        public String itemId;
        public List<String> targetRooms = List.of();
    }

    static class DialogueChoiceData {
        public String question;
        public List<ChoiceData> choices = List.of();
        public DialogueChoiceData followUp;
    }

    static class ChoiceData {
        public String text;
        public boolean correct = false;
        public String response;
    }

    static class RewardsData {
        public List<String> items = List.of();
        public int xp = 0;
    }

    static class CompletionEffectsData {
        public HiddenExitData revealHiddenExit;
        public List<NpcDescriptionUpdateData> updateNpcDescriptions;
        public List<HiddenExitData> resetDiscoveredExits;
    }

    static class HiddenExitData {
        public String roomId;
        public String direction;
    }

    static class NpcDescriptionUpdateData {
        public String npcId;
        public String newDescription;
        public String originalDescription;
    }
}
