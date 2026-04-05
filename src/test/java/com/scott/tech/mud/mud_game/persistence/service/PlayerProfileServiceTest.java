package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedActiveQuest;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedPlayerState;
import com.scott.tech.mud.mud_game.persistence.entity.PlayerProfileEntity;
import com.scott.tech.mud.mud_game.persistence.repository.PlayerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerProfileServiceTest {

    private PlayerProfileRepository profileRepository;
    private PlayerProfileService playerProfileService;

    @BeforeEach
    void setUp() {
        profileRepository = mock(PlayerProfileRepository.class);
        playerProfileService = new PlayerProfileService(profileRepository);
        when(profileRepository.save(any(PlayerProfileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void saveProfileAndRestorePlayerStats_roundTripsActiveQuestProgress() {
        PlayerProfileEntity profile = new PlayerProfileEntity("alice", "forest_fork");
        when(profileRepository.findById("alice")).thenReturn(Optional.of(profile));

        Player player = new Player("player-1", "Alice", "forest_fork");
        player.getQuestState().startQuest("quest_purpose", "objective_find_crossing");
        player.getQuestState().incrementObjectiveProgress("quest_purpose");
        player.getQuestState().incrementObjectiveProgress("quest_purpose");
        player.getQuestState().advanceDialogueStage("quest_purpose");
        player.getQuestState().restoreCompletedQuest("quest_loyalty");

        playerProfileService.saveProfile(player);

        assertThat(profile.getActiveQuests())
                .contains("quest_purpose")
                .contains("objective_find_crossing");

        Player restored = new Player("player-2", "Alice", "town_square");
        playerProfileService.restorePlayerStats("alice", restored);

        assertThat(restored.getQuestState().isQuestActive("quest_purpose")).isTrue();
        assertThat(restored.getQuestState().getActiveQuest("quest_purpose").getCurrentObjectiveId())
                .isEqualTo("objective_find_crossing");
        assertThat(restored.getQuestState().getActiveQuest("quest_purpose").getObjectiveProgress())
                .isEqualTo(2);
        assertThat(restored.getQuestState().getActiveQuest("quest_purpose").getDialogueStage())
                .isEqualTo(1);
        assertThat(restored.getQuestState().isQuestCompleted("quest_loyalty")).isTrue();
    }

    @Test
    void saveFromCache_persistsActiveQuestProgress() {
        PlayerProfileEntity profile = new PlayerProfileEntity("alice", "forest_fork");
        when(profileRepository.findById("alice")).thenReturn(Optional.of(profile));

        CachedPlayerState cachedState = new CachedPlayerState(
                "Alice",
                "forest_fork",
                4,
                "Scout",
                "Human",
                "Ranger",
                "they",
                "them",
                "their",
                "Ready for the trail.",
                "",
                82,
                100,
                24,
                50,
                70,
                100,
                140,
                35,
                List.of("item_lantern"),
                null,
                null,
                "town_square",
                Instant.now(),
                List.of(new CachedActiveQuest(
                        "quest_watchfire",
                        "objective_return_lantern",
                        1,
                        0)),
                List.of("quest_road_report"),
                List.of(),
                List.of()
        );

        playerProfileService.saveFromCache(cachedState);

        assertThat(profile.getActiveQuests())
                .contains("quest_watchfire")
                .contains("objective_return_lantern");
        assertThat(profile.getCompletedQuests()).isEqualTo("quest_road_report");
    }
}
