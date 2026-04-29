package com.scott.tech.mud.mud_game.quest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestPresentationTest {

    @ParameterizedTest
    @CsvSource({
        "5, 3, true",   // level 3 < recommendedLevel 5 → underleveled
        "5, 5, false",  // level 5 == recommendedLevel 5 → not underleveled
        "5, 7, false",  // level 7 > recommendedLevel 5 → not underleveled
        "0, 1, false",  // recommendedLevel 0 → max(1,0)=1, level 1 → not underleveled
        "0, 0, true"    // recommendedLevel 0 → max(1,0)=1, playerLevel 0 < 1 → underleveled
    })
    void isUnderleveled_intOverload(int recommendedLevel, int playerLevel, boolean expected) {
        assertThat(QuestPresentation.isUnderleveled(recommendedLevel, playerLevel)).isEqualTo(expected);
    }

    @Test
    void isUnderleveled_questOverload_delegatesToIntOverload() {
        Quest quest = mock(Quest.class);
        when(quest.recommendedLevel()).thenReturn(10);
        assertThat(QuestPresentation.isUnderleveled(quest, 5)).isTrue();
        assertThat(QuestPresentation.isUnderleveled(quest, 10)).isFalse();
    }

    @Test
    void buildMetaBadges_whenPlayerUnderleveled_includesUnderlevelBadge() {
        String result = QuestPresentation.buildMetaBadges(QuestChallengeRating.HIGH, 10, 5);
        assertThat(result).contains("quest-badge--underleveled");
        assertThat(result).contains("You are Lv.");
    }

    @Test
    void buildMetaBadges_whenPlayerAtLevel_noUnderlevelBadge() {
        String result = QuestPresentation.buildMetaBadges(QuestChallengeRating.MODERATE, 8, 10);
        assertThat(result).doesNotContain("quest-badge--underleveled");
    }

    @Test
    void buildMetaBadges_questOverload_includesChallengeRating() {
        Quest quest = mock(Quest.class);
        when(quest.challengeRating()).thenReturn(QuestChallengeRating.DEADLY);
        when(quest.recommendedLevel()).thenReturn(5);
        String result = QuestPresentation.buildMetaBadges(quest, 3);
        assertThat(result).contains("quest-badge--challenge");
    }

    @Test
    void buildUnderlevelWarning_whenNotUnderleveled_returnsEmpty() {
        Quest quest = mock(Quest.class);
        when(quest.recommendedLevel()).thenReturn(5);
        when(quest.challengeRating()).thenReturn(QuestChallengeRating.LOW);
        assertThat(QuestPresentation.buildUnderlevelWarning(quest, 10)).isEmpty();
    }

    @Test
    void buildUnderlevelWarning_whenUnderleveled_returnsWarning() {
        Quest quest = mock(Quest.class);
        when(quest.recommendedLevel()).thenReturn(10);
        QuestChallengeRating rating = QuestChallengeRating.HIGH;
        when(quest.challengeRating()).thenReturn(rating);
        String result = QuestPresentation.buildUnderlevelWarning(quest, 5);
        assertThat(result).contains("quest-warning");
        assertThat(result).contains("level 10");
    }
}
