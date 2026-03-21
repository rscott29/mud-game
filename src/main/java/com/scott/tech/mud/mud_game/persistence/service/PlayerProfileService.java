package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedPlayerState;
import com.scott.tech.mud.mud_game.persistence.entity.PlayerProfileEntity;
import com.scott.tech.mud.mud_game.persistence.repository.PlayerProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Saves and restores per-player game state (current room, and eventually
 * inventory) so players resume where they left off after reconnecting.
 *
 * <h3>Lifecycle hooks</h3>
 * <ul>
 *   <li><b>Login</b> — {@code LoginHandler} calls {@link #getSavedRoomId} to
 *       restore the player's last room before issuing the welcome response.</li>
 *   <li><b>Disconnect</b> — {@code GameWebSocketHandler.afterConnectionClosed}
 *       calls {@link #saveProfile} to persist the current room before tearing
 *       down the session.</li>
 * </ul>
 */
@Service
@Transactional
public class PlayerProfileService {

    private static final Logger log = LoggerFactory.getLogger(PlayerProfileService.class);

    private final PlayerProfileRepository profileRepository;

    public PlayerProfileService(PlayerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * Returns the room ID last saved for this player, or empty if no profile
     * exists yet (i.e. first login — the caller should use the world's start room).
     */
    @Transactional(readOnly = true)
    public Optional<String> getSavedRoomId(String username) {
        String key = username.toLowerCase();
        Optional<String> roomId = profileRepository.findById(key)
                .map(PlayerProfileEntity::getCurrentRoomId);
        log.debug("getSavedRoomId('{}') -> {}", key, roomId.orElse("(no profile)"));
        return roomId;
    }

    /**
     * Restores level and title from the saved profile onto the player.
     * Safe to call after {@link #getSavedRoomId} during login.
     */
    @Transactional(readOnly = true)
    public void restorePlayerStats(String username, Player player) {
        profileRepository.findById(username.toLowerCase()).ifPresent(p -> {
            player.setLevel(p.getLevel());
            player.setTitle(p.getTitle());
            player.setRace(p.getRace());
            player.setCharacterClass(p.getCharacterClass());
            player.setPronounsSubject(p.getPronounsSubject());
            player.setPronounsObject(p.getPronounsObject());
            player.setPronounsPossessive(p.getPronounsPossessive());
            player.setDescription(p.getDescription());
            player.setHealth(p.getHealth());
            player.setMaxHealth(p.getMaxHealth());
            player.setMana(p.getMana());
            player.setMaxMana(p.getMaxMana());
            player.setMovement(p.getMovement());
            player.setMaxMovement(p.getMaxMovement());
            player.setEquippedWeaponId(p.getEquippedWeaponId());
            player.setExperience(p.getExperience());
            if (p.getRecallRoomId() != null && !p.getRecallRoomId().isBlank()) {
                player.setRecallRoomId(p.getRecallRoomId());
            }
            // Restore completed quests
            if (p.getCompletedQuests() != null && !p.getCompletedQuests().isBlank()) {
                for (String questId : p.getCompletedQuests().split(",")) {
                    player.getQuestState().restoreCompletedQuest(questId.trim());
                }
            }
        });
    }

    /**
     * Checks if this is a new player (no profile exists yet).
     */
    @Transactional(readOnly = true)
    public boolean isNewPlayer(String username) {
        return !profileRepository.existsById(username.toLowerCase());
    }

    /**
     * Saves character creation fields for a new player.
     */
    public void saveCharacterCreation(String username, String currentRoomId, String race, 
                                     String characterClass, String pronounsSubject, 
                                     String pronounsObject, String pronounsPossessive, 
                                     String description) {
        String key = username.toLowerCase();
        PlayerProfileEntity profile = profileRepository.findById(key)
                .orElseGet(() -> new PlayerProfileEntity(key, currentRoomId));
        profile.setRace(race);
        profile.setCharacterClass(characterClass);
        profile.setPronounsSubject(pronounsSubject);
        profile.setPronounsObject(pronounsObject);
        profile.setPronounsPossessive(pronounsPossessive);
        profile.setDescription(description);
        profile.setLastSeenAt(Instant.now());
        profileRepository.save(profile);
        log.debug("Saved character creation for '{}': race='{}' class='{}' pronouns='{}/{}/{}'", 
                  key, race, characterClass, pronounsSubject, pronounsObject, pronounsPossessive);
    }

    /**
     * Creates or updates the player's profile with their current room and the
     * current timestamp.  Safe to call on every disconnect.
     */
    public void saveProfile(String username, String currentRoomId, int level, String title) {
        String key = username.toLowerCase();
        PlayerProfileEntity profile = profileRepository.findById(key)
                .orElseGet(() -> new PlayerProfileEntity(key, currentRoomId));
        profile.setCurrentRoomId(currentRoomId);
        profile.setLevel(level);
        profile.setTitle(title);
        profile.setLastSeenAt(Instant.now());
        profileRepository.save(profile);
        log.debug("Saved profile for '{}': room='{}' level={}", key, currentRoomId, level);
    }

    public void saveProfile(Player player) {
        if (player == null || player.getName() == null) {
            return;
        }
        String key = player.getName().toLowerCase();
        PlayerProfileEntity profile = profileRepository.findById(key)
                .orElseGet(() -> new PlayerProfileEntity(key, player.getCurrentRoomId()));
        profile.setCurrentRoomId(player.getCurrentRoomId());
        profile.setLevel(player.getLevel());
        profile.setTitle(player.getTitle());
        profile.setRace(player.getRace());
        profile.setCharacterClass(player.getCharacterClass());
        profile.setPronounsSubject(player.getPronounsSubject());
        profile.setPronounsObject(player.getPronounsObject());
        profile.setPronounsPossessive(player.getPronounsPossessive());
        profile.setDescription(player.getDescription());
        profile.setHealth(player.getHealth());
        profile.setMaxHealth(player.getMaxHealth());
        profile.setMana(player.getMana());
        profile.setMaxMana(player.getMaxMana());
        profile.setMovement(player.getMovement());
        profile.setMaxMovement(player.getMaxMovement());
        profile.setEquippedWeaponId(player.getEquippedWeaponId());
        profile.setRecallRoomId(player.getRecallRoomId());
        profile.setExperience(player.getExperience());
        // Save completed quests as comma-separated string
        var completedQuests = player.getQuestState().getCompletedQuests();
        profile.setCompletedQuests(completedQuests.isEmpty() ? null : String.join(",", completedQuests));
        profile.setLastSeenAt(Instant.now());
        profileRepository.save(profile);
        log.debug("Saved full profile for '{}': room='{}' hp={}/{} mp={}/{} mv={}/{} xp={}",
                key,
                player.getCurrentRoomId(),
                player.getHealth(), player.getMaxHealth(),
                player.getMana(), player.getMaxMana(),
                player.getMovement(), player.getMaxMovement(),
                player.getExperience());
    }

    /** Backwards-compatible overload that preserves existing level/title. */
    public void saveProfile(String username, String currentRoomId) {
        String key = username.toLowerCase();
        PlayerProfileEntity profile = profileRepository.findById(key)
                .orElseGet(() -> new PlayerProfileEntity(key, currentRoomId));
        profile.setCurrentRoomId(currentRoomId);
        profile.setLastSeenAt(Instant.now());
        profileRepository.save(profile);
        log.debug("Saved profile for '{}': room='{}'", key, currentRoomId);
    }

    /**
     * Saves a player profile from cached state (used by the persistence scheduler).
     */
    public void saveFromCache(CachedPlayerState state) {
        if (state == null || state.name() == null) {
            return;
        }
        String key = state.name().toLowerCase();
        PlayerProfileEntity profile = profileRepository.findById(key)
                .orElseGet(() -> new PlayerProfileEntity(key, state.currentRoomId()));
        profile.setCurrentRoomId(state.currentRoomId());
        profile.setLevel(state.level());
        profile.setTitle(state.title());
        profile.setRace(state.race());
        profile.setCharacterClass(state.characterClass());
        profile.setPronounsSubject(state.pronounsSubject());
        profile.setPronounsObject(state.pronounsObject());
        profile.setPronounsPossessive(state.pronounsPossessive());
        profile.setDescription(state.description());
        profile.setHealth(state.health());
        profile.setMaxHealth(state.maxHealth());
        profile.setMana(state.mana());
        profile.setMaxMana(state.maxMana());
        profile.setMovement(state.movement());
        profile.setMaxMovement(state.maxMovement());
        profile.setEquippedWeaponId(state.equippedWeaponId());
        profile.setRecallRoomId(state.recallRoomId());
        profile.setExperience(state.experience());
        // Save completed quests as comma-separated string
        var completedQuests = state.completedQuests();
        profile.setCompletedQuests(completedQuests == null || completedQuests.isEmpty() ? null : String.join(",", completedQuests));
        profile.setLastSeenAt(state.cachedAt());
        profileRepository.save(profile);
        log.debug("Saved profile from cache for '{}': room='{}'", key, state.currentRoomId());
    }

    /**
     * Updates the completed quests field in the database for a player.
     * Call this after resetting a quest to persist the change.
     */
    public void updateCompletedQuests(String username, java.util.Set<String> completedQuests) {
        String key = username.toLowerCase();
        profileRepository.findById(key).ifPresent(profile -> {
            profile.setCompletedQuests(completedQuests.isEmpty() ? null : String.join(",", completedQuests));
            profile.setLastSeenAt(Instant.now());
            profileRepository.save(profile);
            log.debug("Updated completed quests for '{}': {}", key, completedQuests);
        });
    }
}
