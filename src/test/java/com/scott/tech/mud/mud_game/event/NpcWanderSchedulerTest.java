package com.scott.tech.mud.mud_game.event;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NpcWanderSchedulerTest {

    @Test
    void nextPathIndexStartsAfterCurrentRoomWhenNpcIsRestoredMidPath() {
        List<String> path = List.of(
                "tavern",
                "town_square",
                "blacksmith",
                "town_square",
                "market",
                "town_square",
                "east_road",
                "town_square"
        );

        assertThat(NpcWanderScheduler.nextPathIndex(path, "blacksmith")).isEqualTo(3);
        assertThat(NpcWanderScheduler.nextPathIndex(path, "tavern")).isEqualTo(1);
        assertThat(NpcWanderScheduler.nextPathIndex(path, "unknown_room")).isZero();
    }

    @Test
    void renderDepartureTemplateCollapsesMissingDirectionGracefully() {
        Npc obi = new Npc(
                "npc_dog_obi",
                "Obi",
                "A cheerful dog.",
                List.of("obi"),
                "he",
                "his",
                30,
                90,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                true,
                false,
                false,
                0,
                0,
                0,
                0,
                false
        );

        String withDirection = NpcWanderScheduler.renderDepartureTemplate(
                "Obi gets distracted by something you can't see and scampers off to the {dir}.",
                obi,
                Direction.EAST
        );
        String withoutDirection = NpcWanderScheduler.renderDepartureTemplate(
                "Obi gets distracted by something you can't see and scampers off to the {dir}.",
                obi,
                null
        );

        assertThat(withDirection).isEqualTo("Obi gets distracted by something you can't see and scampers off to the east.");
        assertThat(withoutDirection).isEqualTo("Obi gets distracted by something you can't see and scampers off.");
    }

    @Test
    void renderDepartureTemplateNormalizesAwkwardDirectionPhrasing() {
        Npc obi = new Npc(
                "npc_dog_obi",
                "Obi",
                "A cheerful dog.",
                List.of("obi"),
                "he",
                "his",
                30,
                90,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                true,
                false,
                false,
                0,
                0,
                0,
                0,
                false
        );

        String normalized = NpcWanderScheduler.renderDepartureTemplate(
                "Obi suddenly lets out a confused bark and vanishes down the {dir} in a hurry.",
                obi,
                Direction.NORTH
        );

        assertThat(normalized).isEqualTo("Obi suddenly lets out a confused bark and vanishes to the north in a hurry.");
    }

    @Test
    void renderDepartureTemplateSupportsPronounTokens() {
        Npc obi = new Npc(
                "npc_dog_obi",
                "Obi",
                "A cheerful dog.",
                List.of("obi"),
                "he",
                "his",
                30,
                90,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                true,
                false,
                false,
                0,
                0,
                0,
                0,
                false
        );

        String rendered = NpcWanderScheduler.renderDepartureTemplate(
                "{name} flicks {pronounPossessive} tail and bounds toward the {dir}.",
                obi,
                Direction.WEST
        );

        assertThat(rendered).isEqualTo("Obi flicks his tail and bounds toward the west.");
    }
}
