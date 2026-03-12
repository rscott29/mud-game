package com.scott.tech.mud.mud_game.command.Pickup;

import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.pickup.ValidationResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PickupValidatorTest {

    @Test
    void deniesWhenPrerequisiteMissing_evenForGodPlayer() {
        PickupValidator validator = new PickupValidator();

        Player player = new Player("p1", "Admin", "room_1");
        player.setGod(true);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        Item oath = new Item(
                "item_obis_oath",
                "Obi's Oath",
                "A legendary sword.",
                List.of("oath", "sword"),
                true,
                Rarity.LEGENDARY,
                List.of("item_obis_tag"),
                "The sword does not stir.");

        ValidationResult result = validator.validate(session, oath);

        assertThat(result.allowed()).isFalse();
        assertThat(result.responses()).isNotEmpty();
        assertThat(result.responses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.responses().get(0).message()).isEqualTo("The sword does not stir.");
    }

    @Test
    void allowsWhenPrerequisiteIsPresent() {
        PickupValidator validator = new PickupValidator();

        Player player = new Player("p1", "Player", "room_1");
        player.addToInventory(new Item(
                "item_obis_tag",
                "Worn Collar Tag",
                "A worn tag.",
                List.of("tag"),
                true,
                Rarity.RARE));

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        Item oath = new Item(
                "item_obis_oath",
                "Obi's Oath",
                "A legendary sword.",
                List.of("oath", "sword"),
                true,
                Rarity.LEGENDARY,
                List.of("item_obis_tag"),
                "The sword does not stir.");

        ValidationResult result = validator.validate(session, oath);

        assertThat(result.allowed()).isTrue();
        assertThat(result.responses()).isEmpty();
    }
}
