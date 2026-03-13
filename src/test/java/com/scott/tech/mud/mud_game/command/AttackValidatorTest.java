package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.command.attack.AttackValidationResult;
import com.scott.tech.mud.mud_game.command.attack.AttackValidator;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttackValidatorTest {

    private static final String SESSION_ID = "session-attack-validator";

    private final CombatState combatState = new CombatState();
    private final AttackValidator validator = new AttackValidator(combatState);

    @AfterEach
    void tearDown() {
        combatState.endCombat(SESSION_ID);
    }

    @Test
    void noTargetWhileEngaged_continuesCurrentCombatTarget() {
        Npc dummy = combatNpc("dummy", "Straw Dummy");
        Room room = roomWithNpcs(dummy);
        GameSession session = session(room);

        combatState.engage(SESSION_ID, dummy, room.getId());

        AttackValidationResult result = validator.validate(session, null);

        assertThat(result.allowed()).isTrue();
        assertThat(result.npc()).isSameAs(dummy);
    }

    @Test
    void differentTargetWhileEngaged_isRejected() {
        Npc dummy = combatNpc("dummy", "Straw Dummy");
        Npc wolf = combatNpc("wolf", "Hungry Wolf");
        Room room = roomWithNpcs(dummy, wolf);
        GameSession session = session(room);

        combatState.engage(SESSION_ID, dummy, room.getId());

        AttackValidationResult result = validator.validate(session, "wolf");

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorResponse().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.errorResponse().message()).contains("already fighting");
    }

    @Test
    void staleEngagementWithoutTarget_returnsTargetLostAndEndsCombat() {
        Npc dummy = combatNpc("dummy", "Straw Dummy");
        Room otherRoom = roomWithNpcs();
        GameSession session = session(otherRoom);

        combatState.engage(SESSION_ID, dummy, "training_yard");

        AttackValidationResult result = validator.validate(session, null);

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorResponse().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.errorResponse().message()).contains("no longer here");
        assertThat(combatState.isInCombat(SESSION_ID)).isFalse();
    }

    private static GameSession session(Room room) {
        GameSession session = mock(GameSession.class);
        when(session.getCurrentRoom()).thenReturn(room);
        when(session.getSessionId()).thenReturn(SESSION_ID);
        return session;
    }

    private static Room roomWithNpcs(Npc... npcs) {
        return new Room(
                "training_yard",
                "Training Yard",
                "desc",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of(npcs)
        );
    }

    private static Npc combatNpc(String id, String name) {
        return new Npc(
                id,
                name,
                "desc",
                List.of(id, name.toLowerCase()),
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
                50,
                5,
                1,
                2,
                false
        );
    }
}
