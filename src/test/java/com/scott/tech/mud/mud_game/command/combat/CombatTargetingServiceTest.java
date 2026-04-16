package com.scott.tech.mud.mud_game.command.combat;

import com.scott.tech.mud.mud_game.command.attack.AttackValidationResult;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatTargetingServiceTest {

    private static final CombatTargetingService.CombatMessages MESSAGES = new CombatTargetingService.CombatMessages(
            "combat.no_target",
            "combat.target_lost",
            "combat.target_already_dead",
            "combat.already_engaged",
            "combat.target_not_found",
            "combat.available_targets",
            "combat.cannot_attack"
    );

    private Room room;
    private GameSession session;
    private CombatState combatState;
    private CombatTargetingService service;

    @BeforeEach
    void setUp() {
        room = new Room("room_1", "Training Room", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Player player = new Player("p1", "Hero", "room_1");

        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("room_1")).thenReturn(room);

        session = new GameSession("session-1", player, worldService);
        combatState = new CombatState();
        service = new CombatTargetingService(combatState);
    }

    @Test
    void validate_nullRoom_returnsError() {
        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("room_1")).thenReturn(null);
        Player player = new Player("p2", "Ghost", "room_1");
        GameSession ghostSession = new GameSession("ghost-session", player, worldService);

        AttackValidationResult result = service.validate(ghostSession, "wolf", MESSAGES);

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorResponse().type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void validate_noTarget_noEngage_returnsError() {
        AttackValidationResult result = service.validate(session, null, MESSAGES);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void validate_noTarget_blankString_returnsError() {
        AttackValidationResult result = service.validate(session, "", MESSAGES);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void validate_targetNotFound_returnsError() {
        AttackValidationResult result = service.validate(session, "dragon", MESSAGES);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void validate_targetNotFound_withCombatNpcsInRoom_includesAvailableTargets() {
        Npc dummy = combatNpc("training_dummy", "Training Dummy");
        room.getNpcs().add(dummy);

        AttackValidationResult result = service.validate(session, "dragon", MESSAGES);

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorResponse().message()).contains("Training Dummy");
    }

    @Test
    void validate_foundNpcNotCombatTarget_returnsError() {
        Npc merchant = nonCombatNpc("merchant", "Merchant");
        room.getNpcs().add(merchant);

        AttackValidationResult result = service.validate(session, "merchant", MESSAGES);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void validate_validTarget_alive_allowsAttack() {
        Npc wolf = combatNpc("wolf", "Wolf");
        room.getNpcs().add(wolf);

        AttackValidationResult result = service.validate(session, "wolf", MESSAGES);

        assertThat(result.allowed()).isTrue();
        assertThat(result.npc()).isEqualTo(wolf);
    }

    @Test
    void validate_alreadyEngaged_noTarget_continuesWithEngagedNpc() {
        Npc wolf = combatNpc("wolf", "Wolf");
        room.getNpcs().add(wolf);
        combatState.engage("session-1", wolf, "room_1");

        AttackValidationResult result = service.validate(session, null, MESSAGES);

        assertThat(result.allowed()).isTrue();
        assertThat(result.npc()).isEqualTo(wolf);
    }

    @Test
    void validate_alreadyEngaged_differentTarget_deniesWithEngagedNpcName() {
        Npc wolf = combatNpc("wolf", "Wolf");
        Npc bear = combatNpc("bear", "Bear");
        room.getNpcs().add(wolf);
        room.getNpcs().add(bear);
        combatState.engage("session-1", wolf, "room_1");

        AttackValidationResult result = service.validate(session, "bear", MESSAGES);

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorResponse().message()).contains("Wolf");
    }

    @Test
    void validate_alreadyEngaged_sameTarget_allowsAttack() {
        Npc wolf = combatNpc("wolf", "Wolf");
        room.getNpcs().add(wolf);
        combatState.engage("session-1", wolf, "room_1");

        AttackValidationResult result = service.validate(session, "wolf", MESSAGES);

        assertThat(result.allowed()).isTrue();
        assertThat(result.npc()).isEqualTo(wolf);
    }

    @Test
    void validate_engagedNpcLeftRoom_clearsEngagementAndDenies() {
        Npc wolf = combatNpc("wolf", "Wolf");
        // Engage in room but then wolf is NOT in room
        combatState.engage("session-1", wolf, "room_1");
        // room is empty of NPCs

        AttackValidationResult result = service.validate(session, null, MESSAGES);

        assertThat(result.allowed()).isFalse();
        // combat cleared
        assertThat(combatState.getEncounter("session-1")).isEmpty();
    }

    @Test
    void validate_engagedNpcDead_clearsEngagementAndDenies() {
        Npc wolf = combatNpc("wolf", "Wolf");
        room.getNpcs().add(wolf);
        combatState.engage("session-1", wolf, "room_1");
        // Kill the wolf via the encounter
        combatState.getEncounter("session-1").ifPresent(enc -> enc.applyDamage(9999));

        AttackValidationResult result = service.validate(session, null, MESSAGES);

        assertThat(result.allowed()).isFalse();
        assertThat(combatState.getEncounter("session-1")).isEmpty();
    }

    @Test
    void validate_engagedNpcLeftRoom_withNewTarget_allowsRetargeting() {
        Npc wolf = combatNpc("wolf", "Wolf");
        Npc bear = combatNpc("bear", "Bear");
        // Wolf is engaged but then leaves room; bear is present
        combatState.engage("session-1", wolf, "room_1");
        room.getNpcs().add(bear);

        AttackValidationResult result = service.validate(session, "bear", MESSAGES);

        // After wolf leaves, engagement cleared; bear is valid new target
        assertThat(result.allowed()).isTrue();
        assertThat(result.npc()).isEqualTo(bear);
    }

    // ---- helpers ----

    private static Npc combatNpc(String id, String name) {
        return new Npc(
                id, name, "desc", List.of(id),
                "it", "its",
                0, 0, List.of(), List.of(), List.of(), List.of(),
                false, List.of(), null,
                true, true, 20, 1, 1, 2, false
        );
    }

    private static Npc nonCombatNpc(String id, String name) {
        return new Npc(
                id, name, "desc", List.of(id),
                "they", "their",
                0, 0, List.of(), List.of(), List.of(), List.of(),
                true, List.of(), null,
                false, false, 0, 0, 0, 0, false
        );
    }
}
