package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.ObjectiveEncounterRuntimeService;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatServiceTest {

    private CombatState combatState;
    private CombatService combatService;
    private WorldService worldService;
    private QuestService questService;
    private ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService;

    @BeforeEach
    void setUp() {
        combatState = new CombatState();

        SkillTableService skillTableService = mock(SkillTableService.class);
        when(skillTableService.getPassiveBonuses(anyString(), anyInt()))
                .thenReturn(SkillTableService.PassiveBonuses.ZERO);

        questService = mock(QuestService.class);
        worldService = mock(WorldService.class);
        objectiveEncounterRuntimeService = mock(ObjectiveEncounterRuntimeService.class);

        combatService = new CombatService(
                combatState,
                new CombatStatsResolver(skillTableService),
                new CombatNarrator(),
            questService,
            objectiveEncounterRuntimeService,
            worldService
        );
    }

    @Test
    void nonLethalNpcAttack_doesNotDropPlayerBelowOneHp() {
        Player player = new Player("player-1", "Hero", "room_training_yard");
        player.setHealth(2);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-1");

        CombatEncounter encounter = combatState.engage("session-1", npc("dummy", 5, 5, false), "room_training_yard");
        CombatService.AttackResult result = combatService.executeNpcAttack(session, encounter);

        assertThat(result).isNotNull();
        assertThat(result.playerDefeated()).isFalse();
        assertThat(player.getHealth()).isEqualTo(1);
    }

    @Test
    void lethalNpcAttack_canDefeatPlayerAndEndsCombat() {
        Player player = new Player("player-1", "Hero", "room_wilds");
        player.setHealth(2);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-2");

        CombatEncounter encounter = combatState.engage("session-2", npc("wolf", 5, 5, true), "room_wilds");
        CombatService.AttackResult result = combatService.executeNpcAttack(session, encounter);

        assertThat(result).isNotNull();
        assertThat(result.playerDefeated()).isTrue();
        assertThat(player.getHealth()).isZero();
        assertThat(combatState.isInCombat("session-2")).isFalse();
    }

    @Test
    void defeatingSpawnedNpcInstance_removesItFromRuntimeWorld() {
        Player player = new Player("player-3", "Hero", "room_wilds");
        Npc target = npc("wolf" + Npc.INSTANCE_ID_DELIMITER + "1", 1, 1, true, false, 12);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-3");
        when(questService.onDefeatNpc(player, target))
                .thenReturn(Optional.empty());

        CombatEncounter encounter = combatState.engage("session-3", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        verify(objectiveEncounterRuntimeService).onSpawnedNpcDefeated(player, target);
        verify(worldService).removeNpcInstance("wolf" + Npc.INSTANCE_ID_DELIMITER + "1");
    }

    @Test
    void defeatingNonSpawnedNpc_doesNotRemoveItFromRuntimeWorld() {
        Player player = new Player("player-4", "Hero", "room_wilds");
        Npc target = npc("wolf", 1, 1, true, false, 12);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-4");
        when(questService.onDefeatNpc(player, target))
                .thenReturn(Optional.empty());

        CombatEncounter encounter = combatState.engage("session-4", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        verify(worldService, never()).removeNpcInstance("wolf");
    }

    @Test
    void defeatingNpc_awardsGoldToPlayer() {
        Player player = new Player("player-5", "Hero", "room_wilds");
        Npc target = goldNpc("bandit", 1, 1, true, false, 12, 18);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-5");
        when(questService.onDefeatNpc(player, target)).thenReturn(Optional.empty());

        CombatEncounter encounter = combatState.engage("session-5", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        assertThat(player.getGold()).isEqualTo(18);
        assertThat(result.message()).contains("18").contains("gold");
    }

    @Test
    void defeatingSpawnedNpc_appendsEncounterClearTextAfterBattleLog() {
        Player player = new Player("player-7", "Hero", "room_wilds");
        Npc target = goldNpc("bandit" + Npc.INSTANCE_ID_DELIMITER + "1", 1, 1, true, false, 12, 18);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-7");
        when(questService.onDefeatNpc(player, target)).thenReturn(Optional.empty());
        when(objectiveEncounterRuntimeService.onSpawnedNpcDefeated(player, target))
                .thenReturn(Optional.of("<div class='quest-progress'>The last of 2 undead falls quiet. The path is open again.</div>"));

        CombatEncounter encounter = combatState.engage("session-7", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        assertThat(result.message()).contains("goes down");
        assertThat(result.message()).contains("18").contains("gold");
        assertThat(result.message().indexOf("goes down")).isLessThan(result.message().indexOf("path is open again"));
        assertThat(result.message().indexOf("gold looted")).isLessThan(result.message().indexOf("path is open again"));
    }

    @Test
    void defeatingNpc_asGod_awardsFullGoldDespiteLevelGap() {
        Player player = new Player("player-6", "Immortal", "room_wilds");
        player.setGod(true);
        player.setLevel(100);
        Npc target = goldNpc("bandit", 1, 1, true, false, 12, 18);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-6");
        when(questService.onDefeatNpc(player, target)).thenReturn(Optional.empty());

        CombatEncounter encounter = combatState.engage("session-6", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        assertThat(player.getGold()).isEqualTo(18);
        assertThat(result.message()).contains("18").contains("gold");
    }

    private CombatService.AttackResult defeatTarget(GameSession session, CombatEncounter encounter) {
        CombatService.AttackResult result = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            result = combatService.executePlayerAttack(session, encounter);
            if (result.targetDefeated()) {
                return result;
            }
        }
        throw new AssertionError("Target was not defeated within the expected number of attacks.");
    }

    private static Npc npc(String id, int minDamage, int maxDamage, boolean playerDeathEnabled) {
        return npc(id, minDamage, maxDamage, playerDeathEnabled, true, 100);
    }

    private static Npc npc(String id, int minDamage, int maxDamage, boolean playerDeathEnabled,
                           boolean respawns, int maxHealth) {
        return new Npc(
                id,
                id,
                "desc",
                List.of(id),
                "it",
                "its",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                false,
                true,
                respawns,
                maxHealth,
                1,
                minDamage,
                maxDamage,
                playerDeathEnabled
        );
    }

    private static Npc goldNpc(String id, int minDamage, int maxDamage, boolean playerDeathEnabled,
                               boolean respawns, int maxHealth, int goldReward) {
        return new Npc(
                id,
                id,
                "desc",
                List.of(id),
                "it",
                "its",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                false,
                true,
                respawns,
                maxHealth,
                1,
                0,
                goldReward,
                minDamage,
                maxDamage,
                playerDeathEnabled
        );
    }

    @Test
    void executePlayerUtterance_nonLethal_appendsHealthStatus() {
        Player player = new Player("p-utter-1", "Axi", "room_training");
        player.setLevel(5);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("utter-session-1");

        Npc target = npc("wolf", 1, 1, false, false, 50);
        CombatEncounter encounter = combatState.engage("utter-session-1", target, "room_training");

        CombatService.AttackResult result = combatService.executePlayerUtterance(
                session, encounter, 5,
                null,
                dmg -> "You deal " + dmg + " damage.",
                dmg -> "Axi deals " + dmg + " damage."
        );

        assertThat(result.targetDefeated()).isFalse();
        assertThat(result.encounterEnded()).isFalse();
        assertThat(result.message()).contains("You deal");
    }

    @Test
    void executePlayerUtterance_lethal_endsCombatAndReturnsTargetDefeated() {
        Player player = new Player("p-utter-2", "Axi", "room_training");
        player.setLevel(5);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("utter-session-2");

        Npc target = npc("wolf", 1, 1, false, false, 1);
        CombatEncounter encounter = combatState.engage("utter-session-2", target, "room_training");
        when(questService.onDefeatNpc(player, target)).thenReturn(Optional.empty());

        CombatService.AttackResult result = combatService.executePlayerUtterance(
                session, encounter, 100,
                null,
                dmg -> "You deal " + dmg + " damage.",
                dmg -> "Axi deals " + dmg + " damage."
        );

        assertThat(result.targetDefeated()).isTrue();
        assertThat(result.encounterEnded()).isTrue();
        assertThat(combatState.isInCombat("utter-session-2")).isFalse();
    }

    @Test
    void scaleXpForLevelDifference_sameLevel_returnsFullXp() {
        int scaled = CombatService.scaleXpForLevelDifference(100, 20, 20);
        assertThat(scaled).isEqualTo(100);
    }

    @Test
    void scaleXpForLevelDifference_farAboveTarget_returnsZeroXp() {
        int scaled = CombatService.scaleXpForLevelDifference(100, 30, 10);
        assertThat(scaled).isZero();
    }

    @Test
    void scaleXpForLevelDifference_moderateGap_reducesButKeepsPositiveXp() {
        int scaled = CombatService.scaleXpForLevelDifference(100, 15, 10);
        assertThat(scaled).isBetween(1, 99);
    }
}
