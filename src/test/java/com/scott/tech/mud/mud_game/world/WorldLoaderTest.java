package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorldLoaderTest {

    // -----------------------------------------------------------------------
    // symmetric exits
    // -----------------------------------------------------------------------

    @Test
    void checkExitSymmetry_symmetricPair_noWarnings() {
        // room_a --NORTH--> room_b, room_b --SOUTH--> room_a  ✓
        var rooms = Map.of(
                "room_a", room("room_a", exits(Direction.NORTH, "room_b")),
                "room_b", room("room_b", exits(Direction.SOUTH, "room_a"))
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings);

        assertThat(warnings).isEmpty();
    }

    @Test
    void checkExitSymmetry_multipleSymmetricPairs_noWarnings() {
        // room_a --NORTH--> room_b, room_b --SOUTH--> room_a
        // room_b --EAST-->  room_c, room_c --WEST-->  room_b
        Map<Direction, String> exitsA = exits(Direction.NORTH, "room_b");
        Map<Direction, String> exitsB = exits(Direction.SOUTH, "room_a");
        exitsB.put(Direction.EAST, "room_c");
        Map<Direction, String> exitsC = exits(Direction.WEST, "room_b");

        var rooms = Map.of(
                "room_a", room("room_a", exitsA),
                "room_b", room("room_b", exitsB),
                "room_c", room("room_c", exitsC)
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings);

        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // one-way exits (no return at all)
    // -----------------------------------------------------------------------

    @Test
    void checkExitSymmetry_oneWayExit_producesWarning() {
        // room_a --NORTH--> room_b, but room_b has no SOUTH exit back
        var rooms = Map.of(
                "room_a", room("room_a", exits(Direction.NORTH, "room_b")),
                "room_b", room("room_b", Map.of())
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0))
                .contains("One-way")
                .contains("room_a")
                .contains("NORTH")
                .contains("room_b");
    }

    // -----------------------------------------------------------------------
    // mismatched exit direction — the bug that prompted this feature
    // -----------------------------------------------------------------------

    @Test
    void checkExitSymmetry_wrongDirectionUsed_producesTwoOneWayWarnings() {
        // room_a --WEST--> room_b, but room_b --SOUTH--> room_a
        // (wrong: should be room_a --NORTH--> room_b / room_b --SOUTH--> room_a)
        // Neither EAST (opposite of WEST) exists in room_b,
        // nor NORTH (opposite of SOUTH) exists in room_a → two one-way warnings
        var rooms = Map.of(
                "room_a", room("room_a", exits(Direction.WEST, "room_b")),
                "room_b", room("room_b", exits(Direction.SOUTH, "room_a"))
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings);

        assertThat(warnings).hasSize(2);
        assertThat(warnings).anySatisfy(w ->
                assertThat(w).contains("room_a").contains("WEST").contains("room_b"));
        assertThat(warnings).anySatisfy(w ->
                assertThat(w).contains("room_b").contains("SOUTH").contains("room_a"));
    }

    @Test
    void checkExitSymmetry_returnExistsButPointsElsewhere_producesMismatchedWarning() {
        // room_a --NORTH--> room_b, but room_b --SOUTH--> room_c  (not room_a!)
        var rooms = Map.of(
                "room_a", room("room_a", exits(Direction.NORTH, "room_b")),
                "room_b", room("room_b", exits(Direction.SOUTH, "room_c")),
                "room_c", room("room_c", exits(Direction.NORTH, "room_b"))
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings);

        // room_a NORTH→room_b: room_b.SOUTH is room_c, not room_a → mismatched
        assertThat(warnings).anySatisfy(w ->
                assertThat(w)
                        .contains("Mismatched")
                        .contains("room_a")
                        .contains("NORTH")
                        .contains("room_b")
                        .contains("room_c")
                        .contains("expected back to 'room_a'"));
    }

    // -----------------------------------------------------------------------
    // edge cases
    // -----------------------------------------------------------------------

    @Test
    void checkExitSymmetry_unknownTargetRoom_noWarningOrException() {
        // Unknown targets are already caught as hard errors during load;
        // checkExitSymmetry should skip them gracefully.
        var rooms = Map.of(
                "room_a", room("room_a", exits(Direction.NORTH, "nonexistent"))
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings); // must not throw

        assertThat(warnings).isEmpty();
    }

    @Test
    void checkExitSymmetry_emptyWorld_noWarnings() {
        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(Map.of(), warnings);
        assertThat(warnings).isEmpty();
    }

    // -----------------------------------------------------------------------
    // hidden-entry / asymmetric-direction pattern
    // -----------------------------------------------------------------------

    @Test
    void checkExitSymmetry_secretEntryNormalReturn_noWarning() {
        // Models: forest_fork --hidden EAST--> old_oak_crossing --regular SOUTH--> forest_fork
        // The SOUTH exit from old_oak_crossing has no NORTH return from forest_fork,
        // but forest_fork DOES have a hidden exit pointing to old_oak_crossing,
        // so the asymmetry is intentional — no warning expected.
        Room fork    = roomWithHiddenExit("forest_fork", exits(Direction.WEST, "deep_forest"),
                                          Direction.EAST, "old_oak_crossing");
        Room crossing = room("old_oak_crossing", exits(Direction.SOUTH, "forest_fork"));
        Room deep    = room("deep_forest",       exits(Direction.EAST, "forest_fork"));

        var rooms = Map.of(
                "forest_fork",     fork,
                "old_oak_crossing", crossing,
                "deep_forest",     deep
        );

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(rooms, warnings);

        // The only legitimate asymmetry (SOUTH from crossing, no NORTH from fork) should NOT warn.
        assertThat(warnings).noneMatch(w -> w.contains("old_oak_crossing"));
    }

    @Test
    void checkExitSymmetry_oneWayWithNoHiddenReturn_stillWarns() {
        // A plain one-way exit with no hidden connection back still produces a warning.
        Room a = room("room_a", exits(Direction.NORTH, "room_b"));
        Room b = room("room_b", Map.of()); // no exits at all, no hidden exits

        List<String> warnings = new ArrayList<>();
        WorldLoader.checkExitSymmetry(Map.of("room_a", a, "room_b", b), warnings);

        assertThat(warnings).hasSize(1);
        assertThat(warnings.get(0)).contains("One-way").contains("room_a").contains("NORTH");
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private static Room room(String id, Map<Direction, String> exits) {
        return new Room(id, id, "A room.", exits, List.of(), List.of());
    }

    private static Room roomWithHiddenExit(String id, Map<Direction, String> exits,
                                           Direction hiddenDir, String hiddenTarget) {
        Room r = new Room(id, id, "A room.", exits, List.of(), List.of());
        r.setHiddenExits(Map.of(hiddenDir, hiddenTarget));
        return r;
    }

    private static Map<Direction, String> exits(Direction dir, String targetId) {
        Map<Direction, String> m = new EnumMap<>(Direction.class);
        m.put(dir, targetId);
        return m;
    }
}
