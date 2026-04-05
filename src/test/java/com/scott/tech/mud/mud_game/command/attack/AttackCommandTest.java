package com.scott.tech.mud.mud_game.command.attack;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestPrerequisites;
import com.scott.tech.mud.mud_game.quest.QuestRewards;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttackCommandTest {

    @Test
    void execute_groupMemberStartsFight_engagesWholePartyAndSharesCombatLog() {
        AttackValidator attackValidator = mock(AttackValidator.class);
        CombatService combatService = mock(CombatService.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        GameSession leader = new GameSession("leader-session", new Player("p1", "Axi", "room_training"), worldService);
        GameSession ally = new GameSession("ally-session", new Player("p2", "Nova", "room_training"), worldService);
        Npc wolf = npc("wolf");

        when(attackValidator.validate(leader, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("leader-session", sessionManager, "room_training"))
                .thenReturn(List.of(leader, ally));
        when(combatService.executePlayerAttack(eq(leader), any())).thenReturn(
                CombatService.AttackResult.hit("leader log", "party log")
        );

        AttackCommand command = new AttackCommand(
                "wolf",
                attackValidator,
                combatService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(leader);

        assertThat(combatState.isInCombatWith("leader-session", wolf)).isTrue();
        assertThat(combatState.isInCombatWith("ally-session", wolf)).isTrue();
        assertThat(result.getResponses().getFirst().message()).contains("leader log");
                verify(broadcaster, times(2)).sendToSession(eq("ally-session"), any(GameResponse.class));
        verify(combatLoopScheduler).startCombatLoop("leader-session");
        verify(combatLoopScheduler).startCombatLoop("ally-session");
    }

    @Test
    void execute_groupKill_usesGroupDefeatRoomAction() {
        AttackValidator attackValidator = mock(AttackValidator.class);
        CombatService combatService = mock(CombatService.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        GameSession leader = new GameSession("leader-session", new Player("p1", "Axi", "room_training"), worldService);
        GameSession ally = new GameSession("ally-session", new Player("p2", "Nova", "room_training"), worldService);
        Npc dummy = npc("dummy");

        when(attackValidator.validate(leader, "dummy")).thenReturn(AttackValidationResult.allow(dummy));
        when(partyService.getPartySessionsInRoom("leader-session", sessionManager, "room_training"))
                .thenReturn(List.of(leader, ally));
        when(combatService.executePlayerAttack(eq(leader), any())).thenReturn(
                CombatService.AttackResult.targetDefeat("leader log", "party log", 0, null)
        );

        AttackCommand command = new AttackCommand(
                "dummy",
                attackValidator,
                combatService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(leader);

        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("Axi's group defeats").contains("dummy");
    }

    @Test
    void execute_finalKill_showsQuestCompletionResponses() {
        AttackValidator attackValidator = mock(AttackValidator.class);
        CombatService combatService = mock(CombatService.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        GameSession leader = new GameSession("leader-session", new Player("p1", "Axi", "room_training"), worldService);
        Npc wolf = npc("wolf");
        Item reward = new Item("item_tag_shard_paw", "Paw Shard", "desc", List.of("shard"), true, Rarity.COMMON);
        Quest quest = new Quest(
                "quest_loyalty",
                "The Path of Loyalty",
                "Protect the traveler.",
                "npc_lost_traveler",
                List.of(),
                QuestPrerequisites.NONE,
                List.of(),
                new QuestRewards(List.of(), 50, 25),
                List.of("The traveler sinks to their knees, trembling but alive."),
                QuestCompletionEffects.NONE
        );
        QuestService.QuestProgressResult questResult = QuestService.QuestProgressResult.questComplete(
                quest,
                List.of("The traveler sinks to their knees, trembling but alive."),
                List.of(reward),
                50,
                25,
                QuestCompletionEffects.NONE,
                com.scott.tech.mud.mud_game.quest.ObjectiveEffects.NONE
        );

        when(attackValidator.validate(leader, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("leader-session", sessionManager, "room_training"))
                .thenReturn(List.of(leader));
        when(levelingService.getXpTables()).thenReturn(xpTables);
        when(levelingService.addExperience(leader.getPlayer(), 50))
                .thenReturn(LevelingService.XpGainResult.noLevelUp(50, 50, leader.getPlayer().getLevel()));
        when(combatService.executePlayerAttack(eq(leader), any())).thenReturn(
                CombatService.AttackResult.targetDefeat("leader log", "party log", 0, questResult)
        );

        AttackCommand command = new AttackCommand(
                "wolf",
                attackValidator,
                combatService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(leader);
        String combinedMessages = result.getResponses().stream()
                .map(GameResponse::message)
                .filter(message -> message != null && !message.isBlank())
                .reduce("", (left, right) -> left + "\n" + right);

        assertThat(result.getResponses())
                .extracting(GameResponse::message)
                .anyMatch(message -> message != null && message.contains("The traveler sinks to their knees"))
                .anyMatch(message -> message != null && message.contains("Quest Complete"))
                .anyMatch(message -> message != null && message.contains("50"))
                .anyMatch(message -> message != null && message.contains("25"))
                .anyMatch(message -> message != null && message.contains("Paw Shard"));
        assertThat(countOccurrences(combinedMessages, "The traveler sinks to their knees, trembling but alive.")).isEqualTo(1);
        assertThat(countOccurrences(combinedMessages, "🏆 Quest Complete: <strong>The Path of Loyalty</strong>!")).isEqualTo(1);
        assertThat(countOccurrences(combinedMessages, "✨ You gain <span class='xp-value'>50</span> experience points.")).isEqualTo(1);
        assertThat(countOccurrences(combinedMessages, "💰 You receive <span class='gold-value'>25</span> gold.")).isEqualTo(1);
        assertThat(countOccurrences(combinedMessages, "📦 You receive: <strong>Paw Shard</strong>")).isEqualTo(1);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Npc npc(String id) {
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
                true,
                true,
                20,
                1,
                1,
                2,
                true
        );
    }
}
