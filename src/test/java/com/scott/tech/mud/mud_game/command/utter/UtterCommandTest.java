package com.scott.tech.mud.mud_game.command.utter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.command.attack.AttackValidationResult;
import com.scott.tech.mud.mud_game.combat.CombatEffect;
import com.scott.tech.mud.mud_game.combat.CombatEffectType;
import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.magic.MagicCastingService;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UtterCommandTest {

    @Test
    void fragmentChanceForLevel_scalesUpAndCaps() {
        SkillTableService skillTableService = skillTableService();

        SkillTableService.SkillDefinition whisper = skillTableService.findSkill("whisperbinder", UtterService.WHISPER_OF_DOUBT_ID)
                .orElseThrow();
        SkillTableService.SkillDefinition wound = skillTableService.findSkill("whisperbinder", UtterService.NAME_THE_WOUND_ID)
                .orElseThrow();

        assertThat(whisper.activeMagic().fragmentBuild().chanceForLevel(1)).isEqualTo(55);
        assertThat(whisper.activeMagic().fragmentBuild().chanceForLevel(25)).isEqualTo(85);
        assertThat(wound.activeMagic().fragmentBuild().chanceForLevel(22)).isEqualTo(95);
    }

    @Test
    void execute_nonWhisperbinderReturnsError() {
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService(), combatService, 1);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        CombatState combatState = new CombatState();
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        WorldService worldService = mock(WorldService.class);

        Player player = new Player("p1", "Axi", "room_training");
        player.setCharacterClass("Warrior");
        GameSession session = new GameSession("utter-session", player, worldService);

        UtterCommand command = new UtterCommand(
                List.of("wolf"),
                utterValidator,
                utterService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().message()).contains("Only Whisperbinders");
        verifyNoInteractions(utterValidator);
    }

    @Test
    void execute_consumesManaStartsCombatAndSharesPartyLog() {
        SkillTableService skillTableService = skillTableService();
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService, combatService, 1);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        GameSession leader = new GameSession("leader-session", new Player("p1", "Axi", "room_training"), worldService);
        leader.getPlayer().setCharacterClass("Whisperbinder");
        leader.getPlayer().setLevel(4);
        leader.getPlayer().setMana(30);
        leader.getPlayer().setMaxMana(125);

        GameSession ally = new GameSession("ally-session", new Player("p2", "Nova", "room_training"), worldService);
        ally.getPlayer().setCharacterClass("Warrior");

        Npc wolf = npc("wolf");

        when(utterValidator.validate(leader, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("leader-session", sessionManager, "room_training"))
                .thenReturn(List.of(leader, ally));
        when(combatService.executePlayerUtterance(eq(leader), any(), anyInt(), any(Runnable.class), any(), any()))
                .thenAnswer(invocation -> {
                    Runnable beforeApplyDamage = invocation.getArgument(3);
           
                    IntFunction<String> playerMessageFactory = invocation.getArgument(4);
    
                    IntFunction<String> partyMessageFactory = invocation.getArgument(5);
                    beforeApplyDamage.run();
                    return CombatService.AttackResult.hit(playerMessageFactory.apply(7), partyMessageFactory.apply(7));
                });

        UtterCommand command = new UtterCommand(
                List.of("wolf"),
                utterValidator,
                utterService,
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
        CombatEncounter encounter = combatState.getEncounter("leader-session").orElseThrow();

        assertThat(combatState.isInCombatWith("leader-session", wolf)).isTrue();
        assertThat(combatState.isInCombatWith("ally-session", wolf)).isTrue();
        assertThat(encounter.effectPotency(CombatEffectType.WHISPERBINDER_FRAGMENTS)).isEqualTo(1);
        assertThat(leader.getPlayer().getMana()).isEqualTo(25);
        assertThat(result.getResponses().getFirst().message()).contains("splinter of doubt");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("cutting utterance").contains("wolf");
        verify(broadcaster, times(2)).sendToSession(eq("ally-session"), any(GameResponse.class));
        verify(combatLoopScheduler).startCombatLoop("leader-session");
        verify(combatLoopScheduler).startCombatLoop("ally-session");
    }

    @Test
    void execute_failedFragmentRollKeepsTargetUnmarked() {
        SkillTableService skillTableService = skillTableService();
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService, combatService, 100);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        GameSession session = new GameSession("whiff-session", new Player("p1", "Axi", "room_training"), worldService);
        session.getPlayer().setCharacterClass("Whisperbinder");
        session.getPlayer().setLevel(1);
        session.getPlayer().setMana(30);
        session.getPlayer().setMaxMana(125);

        Npc wolf = npc("wolf");

        when(utterValidator.validate(session, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("whiff-session", sessionManager, "room_training"))
                .thenReturn(List.of(session));
        when(combatService.executePlayerUtterance(eq(session), any(), anyInt(), any(Runnable.class), any(), any()))
                .thenAnswer(invocation -> {
                    Runnable beforeApplyDamage = invocation.getArgument(3);
                    IntFunction<String> playerMessageFactory = invocation.getArgument(4);
                    IntFunction<String> partyMessageFactory = invocation.getArgument(5);
                    beforeApplyDamage.run();
                    return CombatService.AttackResult.hit(playerMessageFactory.apply(6), partyMessageFactory.apply(6));
                });

        UtterCommand command = new UtterCommand(
                List.of("wolf"),
                utterValidator,
                utterService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(session);
        CombatEncounter encounter = combatState.getEncounter("whiff-session").orElseThrow();

        assertThat(encounter.effectPotency(CombatEffectType.WHISPERBINDER_FRAGMENTS)).isZero();
        assertThat(result.getResponses().getFirst().message()).contains("No new fragment catches hold");
        verify(combatLoopScheduler).startCombatLoop("whiff-session");
    }

    @Test
    void execute_willBonusLetsBorderlineFragmentRollLand() {
        SkillTableService skillTableService = skillTableService();
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService, combatService, 58);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        GameSession session = new GameSession("will-session", new Player("p1", "Axi", "room_training"), worldService);
        session.getPlayer().setCharacterClass("Whisperbinder");
        session.getPlayer().setLevel(1);
        session.getPlayer().setWill(1);
        session.getPlayer().setMana(30);
        session.getPlayer().setMaxMana(125);

        Npc wolf = npc("wolf");

        when(utterValidator.validate(session, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("will-session", sessionManager, "room_training"))
                .thenReturn(List.of(session));
        when(combatService.executePlayerUtterance(eq(session), any(), anyInt(), any(Runnable.class), any(), any()))
                .thenAnswer(invocation -> {
                    Runnable beforeApplyDamage = invocation.getArgument(3);
                    IntFunction<String> playerMessageFactory = invocation.getArgument(4);
                    IntFunction<String> partyMessageFactory = invocation.getArgument(5);
                    beforeApplyDamage.run();
                    return CombatService.AttackResult.hit(playerMessageFactory.apply(6), partyMessageFactory.apply(6));
                });

        UtterCommand command = new UtterCommand(
                List.of("wolf"),
                utterValidator,
                utterService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(session);
        CombatEncounter encounter = combatState.getEncounter("will-session").orElseThrow();

        assertThat(encounter.effectPotency(CombatEffectType.WHISPERBINDER_FRAGMENTS)).isEqualTo(1);
        assertThat(result.getResponses().getFirst().message()).contains("Frayed");
        verify(combatLoopScheduler).startCombatLoop("will-session");
    }

    @Test
    void execute_traceUtteranceOpensWithTwoFragmentsWithoutDirectDamage() {
        SkillTableService skillTableService = skillTableService();
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService, combatService, 1);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        Player player = new Player("p1", "Axi", "room_training");
        player.setCharacterClass("Whisperbinder");
        player.setLevel(1);
        player.setMana(30);
        player.setMaxMana(125);
        GameSession session = new GameSession("trace-session", player, worldService);
        Npc wolf = npc("wolf");

        when(utterValidator.validate(session, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("trace-session", sessionManager, "room_training"))
                .thenReturn(List.of(session));

        UtterCommand command = new UtterCommand(
                List.of("trace", "wolf"),
                utterValidator,
                utterService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(session);
        CombatEncounter encounter = combatState.getEncounter("trace-session").orElseThrow();

        assertThat(combatState.isInCombatWith("trace-session", wolf)).isTrue();
        assertThat(player.getMana()).isEqualTo(26);
        assertThat(encounter.effectPotency(CombatEffectType.WHISPERBINDER_FRAGMENTS)).isEqualTo(2);
        assertThat(result.getResponses().getFirst().message()).contains("trace the echo");
        verifyNoInteractions(combatService);
        verify(combatLoopScheduler).startCombatLoop("trace-session");
    }

        @Test
    void execute_namedUtteranceAppliesLingeringPhraseEffect() {
        SkillTableService skillTableService = skillTableService();
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService, combatService, 1);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        CombatState combatState = new CombatState();
        WorldService worldService = mock(WorldService.class);

        Player player = new Player("p1", "Axi", "room_training");
        player.setCharacterClass("Whisperbinder");
        player.setLevel(6);
        player.setMana(20);
        GameSession session = new GameSession("utter-session", player, worldService);
        Npc wolf = npc("wolf");

        when(utterValidator.validate(session, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
        when(partyService.getPartySessionsInRoom("utter-session", sessionManager, "room_training"))
                .thenReturn(List.of(session));

        UtterCommand command = new UtterCommand(
                List.of("lingering", "wolf"),
                utterValidator,
                utterService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(session);

        CombatEncounter encounter = combatState.getEncounter("utter-session").orElseThrow();

        assertThat(result.getResponses().getFirst().message()).contains("lingering phrase");
        assertThat(player.getMana()).isEqualTo(11);
        assertThat(encounter.getEffect(CombatEffectType.LINGERING_PHRASE)).isPresent();
        assertThat(encounter.effectPotency(CombatEffectType.WHISPERBINDER_FRAGMENTS)).isEqualTo(1);
        verify(combatLoopScheduler).startCombatLoop("utter-session");
    }

        @Test
        void execute_hushOnNamedTargetAppliesExtendedDuration() {
                SkillTableService skillTableService = skillTableService();
                UtterValidator utterValidator = mock(UtterValidator.class);
                CombatService combatService = mock(CombatService.class);
                UtterService utterService = utterService(skillTableService, combatService, 1);
                CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
                ExperienceTableService xpTables = mock(ExperienceTableService.class);
                GameSessionManager sessionManager = mock(GameSessionManager.class);
                PartyService partyService = mock(PartyService.class);
                WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
                LevelingService levelingService = mock(LevelingService.class);
                CombatState combatState = new CombatState();
                WorldService worldService = mock(WorldService.class);

                Player player = new Player("p1", "Axi", "room_training");
                player.setCharacterClass("Whisperbinder");
                player.setLevel(12);
                player.setMana(30);
                player.setMaxMana(125);
                GameSession session = new GameSession("hush-session", player, worldService);
                Npc wolf = npc("wolf");

                when(utterValidator.validate(session, "wolf")).thenReturn(AttackValidationResult.allow(wolf));
                when(partyService.getPartySessionsInRoom("hush-session", sessionManager, "room_training"))
                                .thenReturn(List.of(session));

                CombatEncounter encounter = combatState.engage(session.getSessionId(), wolf, "room_training");
                encounter.applyEffect(new CombatEffect(CombatEffectType.WHISPERBINDER_FRAGMENTS, session.getSessionId(), 2, 99));

                UtterCommand command = new UtterCommand(
                                List.of("hush", "wolf"),
                                utterValidator,
                                utterService,
                                combatLoopScheduler,
                                combatState,
                                xpTables,
                                sessionManager,
                                partyService,
                                broadcaster,
                                levelingService,
                                worldService
                );

                var result = command.execute(session);

                assertThat(player.getMana()).isEqualTo(14);
                assertThat(encounter.getEffect(CombatEffectType.HUSH).map(CombatEffect::remainingTurns)).hasValue(2);
                assertThat(result.getResponses().getFirst().message()).contains("wolf");
                verify(combatLoopScheduler).startCombatLoop("hush-session");
        }

    @Test
    void execute_requiresEnoughManaForSelectedUtterance() {
        UtterValidator utterValidator = mock(UtterValidator.class);
        CombatService combatService = mock(CombatService.class);
        UtterService utterService = utterService(skillTableService(), combatService, 1);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        CombatState combatState = new CombatState();
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PartyService partyService = mock(PartyService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        WorldService worldService = mock(WorldService.class);

        Player player = new Player("p1", "Axi", "room_training");
        player.setCharacterClass("Whisperbinder");
        player.setLevel(10);
        player.setMana(10);
        GameSession session = new GameSession("utter-session", player, worldService);

        UtterCommand command = new UtterCommand(
                List.of("sever", "wolf"),
                utterValidator,
                utterService,
                combatLoopScheduler,
                combatState,
                xpTables,
                sessionManager,
                partyService,
                broadcaster,
                levelingService,
                worldService
        );

        var result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().message()).contains("Sever the Name").contains("20 mana");
        verifyNoInteractions(utterValidator);
    }

    private static SkillTableService skillTableService() {
        return new SkillTableService(new ObjectMapper());
    }

    private static UtterService utterService(SkillTableService skillTableService,
                                             CombatService combatService,
                                             int fragmentRoll) {
        MagicCastingService magicCastingService = new MagicCastingService(skillTableService, combatService, () -> fragmentRoll);
        return new UtterService(magicCastingService);
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