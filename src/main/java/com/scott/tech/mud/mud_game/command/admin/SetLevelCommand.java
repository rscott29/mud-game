package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

import java.util.List;
import java.util.Optional;

/**
 * God-only command that sets a player's level.
 * Usage: setlevel <player> <level>
 *        setlevel <level>  (sets own level)
 */
public class SetLevelCommand implements GameCommand {

    private final String rawArgs;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;
    private final ExperienceTableService xpTables;
    private final LevelingService levelingService;
    private final PlayerProfileService playerProfileService;
    private final PlayerStateCache stateCache;

    public SetLevelCommand(String rawArgs, GameSessionManager sessionManager, 
                           WorldBroadcaster worldBroadcaster, ExperienceTableService xpTables,
                           LevelingService levelingService,
                           PlayerProfileService playerProfileService, PlayerStateCache stateCache) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
        this.xpTables = xpTables;
        this.levelingService = levelingService;
        this.playerProfileService = playerProfileService;
        this.stateCache = stateCache;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.setlevel.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.setlevel.usage")));
        }

        String[] parts = rawArgs.split("\\s+", 2);
        
        GameSession targetSession;
        Player targetPlayer;
        int newLevel;
        String targetName;

        if (parts.length == 1) {
            // setlevel <level> - set own level
            try {
                newLevel = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                return CommandResult.of(GameResponse.error(Messages.get("command.setlevel.invalid_level")));
            }
            targetSession = session;
            targetPlayer = session.getPlayer();
            targetName = targetPlayer.getName();
        } else {
            // setlevel <player> <level>
            targetName = parts[0];
            try {
                newLevel = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return CommandResult.of(GameResponse.error(Messages.get("command.setlevel.invalid_level")));
            }
            
            // Find target player
            Optional<GameSession> targetSessionOpt = sessionManager.findPlayingByName(targetName);
            if (targetSessionOpt.isEmpty()) {
                return CommandResult.of(GameResponse.error(
                        Messages.fmt("command.setlevel.player_not_found", "player", targetName)));
            }
            targetSession = targetSessionOpt.get();
            targetPlayer = targetSession.getPlayer();
            targetName = targetPlayer.getName(); // Use actual casing
        }

        // Validate level range
        int maxLevel = xpTables.getMaxLevel(targetPlayer.getCharacterClass());
        if (newLevel < 1 || newLevel > maxLevel) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.setlevel.level_out_of_range", 
                            "min", "1", 
                            "max", String.valueOf(maxLevel))));
        }

        int oldLevel = targetPlayer.getLevel();
        targetPlayer.setLevel(newLevel);
        
        // Set XP to the threshold for that level
        int xpForLevel = xpTables.getXpForLevel(targetPlayer.getCharacterClass(), newLevel);
        targetPlayer.setExperience(xpForLevel);
        Optional<String> levelUpMessage = levelingService.applyManualLevelChange(targetPlayer, oldLevel, newLevel);
        List<String> unlockedSkills = levelingService.getNewlyUnlockedSkillNames(targetPlayer, oldLevel, newLevel);

        // Persist to database immediately so changes survive reconnect/refresh
        playerProfileService.saveProfile(targetPlayer);

        // Evict stale cache so DB state is used on next login
        stateCache.evict(targetName.toLowerCase());

        // Broadcast level-up to the world if level increased
        if (levelUpMessage.isPresent()) {
            String worldMessageKey = (newLevel - oldLevel) > 1 ? "level.up.world.multi" : "level.up.world";
            String levelUpBroadcast = Messages.fmt(worldMessageKey,
                    "name", targetName,
                    "level", String.valueOf(newLevel));
            worldBroadcaster.broadcastToAll(GameResponse.narrative(levelUpBroadcast));
        }

        // Notify the god who executed the command
        String message = Messages.fmt("command.setlevel.success",
                "player", targetName,
                "old_level", String.valueOf(oldLevel),
                "new_level", String.valueOf(newLevel));

        // If target is different from executor, notify the target.
        if (targetPlayer != session.getPlayer()) {
            String targetMessage = Messages.fmt("command.setlevel.target_notify",
                    "level", String.valueOf(newLevel));
            worldBroadcaster.sendToSession(targetSession.getSessionId(),
                    GameResponse.narrative(targetMessage));
        }

        if (levelUpMessage.isPresent()) {
            worldBroadcaster.sendToSession(targetSession.getSessionId(),
                    GameResponse.narrative(levelUpMessage.get()).withPlayerStats(targetPlayer, xpTables));
        } else {
            worldBroadcaster.sendToSession(targetSession.getSessionId(),
                    GameResponse.playerStatsUpdate(targetPlayer, xpTables));
        }

        for (String skillName : unlockedSkills) {
            worldBroadcaster.sendToSession(targetSession.getSessionId(),
                    GameResponse.narrative(Messages.fmt("skill.unlock", "skill", skillName)));
        }

        return CommandResult.of(GameResponse.narrative(message));
    }
}
