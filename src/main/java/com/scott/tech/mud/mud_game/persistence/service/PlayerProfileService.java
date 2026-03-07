package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.model.Player;
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
        return profileRepository.findById(username.toLowerCase())
                .map(PlayerProfileEntity::getCurrentRoomId);
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
        });
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
}
