package com.scott.tech.mud.mud_game.service;

import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry.ClassStats;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Handles player leveling: XP gain, level up checks, and stat increases on level up.
 */
@Service
public class LevelingService {

    private static final Logger log = LoggerFactory.getLogger(LevelingService.class);

    private final ExperienceTableService xpTables;
    private final CharacterClassStatsRegistry classStats;
    private final SkillTableService skillTableService;

    public LevelingService(ExperienceTableService xpTables,
                           CharacterClassStatsRegistry classStats,
                           SkillTableService skillTableService) {
        this.xpTables = xpTables;
        this.classStats = classStats;
        this.skillTableService = skillTableService;
    }

    /**
     * Returns the underlying experience table service for direct XP queries.
     */
    public ExperienceTableService getXpTables() {
        return xpTables;
    }

    /**
     * Result of adding XP to a player.
     */
    public record XpGainResult(
            int xpGained,
            int totalXp,
            boolean leveledUp,
            int oldLevel,
            int newLevel,
            String levelUpMessage
    ) {
        public static XpGainResult noLevelUp(int xpGained, int totalXp, int level) {
            return new XpGainResult(xpGained, totalXp, false, level, level, null);
        }

        public static XpGainResult withLevelUp(int xpGained, int totalXp, int oldLevel, int newLevel, String message) {
            return new XpGainResult(xpGained, totalXp, true, oldLevel, newLevel, message);
        }
    }

    /**
     * Adds XP to a player and checks for level up.
     * If the player levels up, their stats are increased and a message is generated.
     * 
     * @param player the player gaining XP
     * @param xpAmount the amount of XP gained
     * @return result containing level up info if applicable
     */
    public XpGainResult addExperience(Player player, int xpAmount) {
        if (xpAmount <= 0) {
            return XpGainResult.noLevelUp(0, player.getExperience(), player.getLevel());
        }

        int oldLevel = player.getLevel();
        player.addExperience(xpAmount);
        int totalXp = player.getExperience();

        // Check for level up
        Optional<Integer> newLevelOpt = xpTables.checkLevelUp(
                player.getCharacterClass(),
                oldLevel,
                totalXp
        );

        if (newLevelOpt.isPresent()) {
            int newLevel = newLevelOpt.get();
            player.setLevel(newLevel);
            String message = applyLevelUpEffects(player, oldLevel, newLevel);
            return XpGainResult.withLevelUp(xpAmount, totalXp, oldLevel, newLevel, message);
        }

        return XpGainResult.noLevelUp(xpAmount, totalXp, oldLevel);
    }

    /**
     * Applies normal level-up effects for explicit/manual level changes
     * (e.g., admin set-level command).
     *
     * @return level-up message when levels increased, otherwise empty
     */
    public Optional<String> applyManualLevelChange(Player player, int oldLevel, int newLevel) {
        if (newLevel > oldLevel) {
            // Level increased - apply level-up effects
            return Optional.of(applyLevelUpEffects(player, oldLevel, newLevel));
        } else {
            // Level decreased OR same level - recalculate stats to ensure correctness
            recalculateStatsForLevel(player, newLevel);
            return Optional.empty();
        }
    }

    /**
     * Recalculates and sets stats for a player at a specific level.
     * Used when manually setting level (especially when decreasing).
     * Stats = baseClassStats + (level - 1) * growthPerLevel
     */
    private void recalculateStatsForLevel(Player player, int level) {
        Optional<ClassStats> stats = classStats.findByName(player.getCharacterClass());
        if (stats.isEmpty()) {
            log.warn("No class stats found for '{}'", player.getCharacterClass());
            return;
        }

        ClassStats cs = stats.get();
        int levelsGained = level - 1; // How many levels above 1
        
        int healthPerLevel = calculateStatGrowth(cs.maxHealth(), 100, 3, 5);
        int manaPerLevel = calculateStatGrowth(cs.maxMana(), 50, 2, 4);
        int movementPerLevel = calculateStatGrowth(cs.maxMovement(), 100, 1, 2);

        // Calculate stats at the target level
        int newMaxHealth = cs.maxHealth() + (healthPerLevel * levelsGained);
        int newMaxMana = cs.maxMana() + (manaPerLevel * levelsGained);
        int newMaxMovement = cs.maxMovement() + (movementPerLevel * levelsGained);

        player.setMaxHealth(newMaxHealth);
        player.setMaxMana(newMaxMana);
        player.setMaxMovement(newMaxMovement);

        // Restore current stats to new max
        player.setHealth(newMaxHealth);
        player.setMana(newMaxMana);
        player.setMovement(newMaxMovement);
    }

    private String applyLevelUpEffects(Player player, int oldLevel, int newLevel) {
        applyLevelUpStats(player, oldLevel, newLevel);
        String message = generateLevelUpMessage(player, oldLevel, newLevel);
        log.info("Player {} leveled up from {} to {}", player.getName(), oldLevel, newLevel);
        return message;
    }

    /**
     * Applies stat increases when a player levels up.
     * Stats scale based on class base stats and level progression.
     */
    private void applyLevelUpStats(Player player, int oldLevel, int newLevel) {
        Optional<ClassStats> stats = classStats.findByName(player.getCharacterClass());
        if (stats.isEmpty()) {
            return;
        }

        ClassStats cs = stats.get();
        int levelsGained = newLevel - oldLevel;

        // Calculate stat bonuses per level based on class
        // Health: +3 to +5 per level depending on class
        // Mana: +2 to +4 per level depending on class  
        // Movement: +1 to +2 per level depending on class
        int healthPerLevel = calculateStatGrowth(cs.maxHealth(), 100, 3, 5);
        int manaPerLevel = calculateStatGrowth(cs.maxMana(), 50, 2, 4);
        int movementPerLevel = calculateStatGrowth(cs.maxMovement(), 100, 1, 2);

        int healthBonus = healthPerLevel * levelsGained;
        int manaBonus = manaPerLevel * levelsGained;
        int movementBonus = movementPerLevel * levelsGained;

        // Increase max stats
        player.setMaxHealth(player.getMaxHealth() + healthBonus);
        player.setMaxMana(player.getMaxMana() + manaBonus);
        player.setMaxMovement(player.getMaxMovement() + movementBonus);

        // Fully restore stats on level up
        player.setHealth(player.getMaxHealth());
        player.setMana(player.getMaxMana());
        player.setMovement(player.getMaxMovement());
    }

    /**
     * Calculates per-level stat growth based on class base stat.
     * Classes with higher base stats get more per level.
     */
    private int calculateStatGrowth(int baseStat, int average, int minGrowth, int maxGrowth) {
        double ratio = (double) baseStat / average;
        int growth = (int) Math.round(minGrowth + (maxGrowth - minGrowth) * (ratio - 0.5));
        return Math.max(minGrowth, Math.min(maxGrowth, growth));
    }

    /**
     * Generates a level up message for the player.
     */
    private String generateLevelUpMessage(Player player, int oldLevel, int newLevel) {
        if (newLevel - oldLevel > 1) {
            return Messages.fmt("level.up.multi", 
                    "name", player.getName(),
                    "old_level", String.valueOf(oldLevel),
                    "new_level", String.valueOf(newLevel));
        }
        return Messages.fmt("level.up", 
                "name", player.getName(),
                "level", String.valueOf(newLevel));
    }

    /**
     * Returns the XP needed to reach the next level for display purposes.
     */
    public int getXpForNextLevel(Player player) {
        return xpTables.getXpToNextLevel(player.getCharacterClass(), player.getLevel());
    }

    /**
     * Returns the XP progress within the current level for progress bar display.
     */
    public int getXpProgressInLevel(Player player) {
        return xpTables.getXpProgressInLevel(
                player.getCharacterClass(), 
                player.getExperience(), 
                player.getLevel()
        );
    }

    /**
     * Returns the max level for a player's class.
     */
    public int getMaxLevel(Player player) {
        return xpTables.getMaxLevel(player.getCharacterClass());
    }

    /**
     * Checks if a player is at max level.
     */
    public boolean isMaxLevel(Player player) {
        return player.getLevel() >= getMaxLevel(player);
    }

    /**
     * Returns names of skills unlocked between oldLevel (exclusive) and newLevel (inclusive).
     */
    public List<String> getNewlyUnlockedSkillNames(Player player, int oldLevel, int newLevel) {
        return skillTableService.getNewlyUnlockedSkillNames(player.getCharacterClass(), oldLevel, newLevel);
    }
}
