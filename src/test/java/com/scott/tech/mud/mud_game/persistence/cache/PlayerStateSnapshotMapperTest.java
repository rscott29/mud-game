package com.scott.tech.mud.mud_game.persistence.cache;

import com.scott.tech.mud.mud_game.consumable.ActiveConsumableEffect;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectType;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStateSnapshotMapperTest {

    @Test
    void snapshotAndRestore_roundTripsPlayerSessionState() {
        WorldService sourceWorld = mock(WorldService.class);
        Room tavern = new Room("tavern", "Tavern", "Warm lamplight.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(sourceWorld.getRoom("tavern")).thenReturn(tavern);

        GameSession sourceSession = new GameSession("s1", new Player("p1", "Alice", "tavern"), sourceWorld);
        Player sourcePlayer = sourceSession.getPlayer();
        sourcePlayer.setLevel(7);
        sourcePlayer.setTitle("Veteran");
        sourcePlayer.setRace("Human");
        sourcePlayer.setCharacterClass("Ashen Knight");
        sourcePlayer.setPronounsSubject("they");
        sourcePlayer.setPronounsObject("them");
        sourcePlayer.setPronounsPossessive("their");
        sourcePlayer.setDescription("A seasoned traveler.");
        sourcePlayer.setModerationFilters("harassment");
        sourcePlayer.setHealth(82);
        sourcePlayer.setMaxHealth(110);
        sourcePlayer.setMana(35);
        sourcePlayer.setMaxMana(60);
        sourcePlayer.setMovement(74);
        sourcePlayer.setMaxMovement(120);
        sourcePlayer.setExperience(345);
        sourcePlayer.setGold(19);
        sourcePlayer.setRecallRoomId("town_square");
        sourcePlayer.getQuestState().restoreCompletedQuest("quest_intro");
        sourcePlayer.getQuestState().restoreActiveQuest("quest_wolves", "objective_defend", 2, 1);

        Item sword = new Item("iron_sword", "Iron Sword", "A steel blade.", List.of("sword"), true, Rarity.COMMON);
        sourcePlayer.addToInventory(sword);
        sourcePlayer.setEquippedItemsSerialized("main_weapon=iron_sword");
        sourceSession.addFollower("npc_obi");
        sourceSession.addActiveConsumableEffect(new ActiveConsumableEffect(
                "item_odd_mushroom",
                "Odd Mushroom",
                ConsumableEffectType.DAMAGE_OVER_TIME,
                6,
                5,
                3,
                Instant.now().plusSeconds(10),
                "The numbness recedes.",
                List.of(),
                null
        ));

        Instant snapshotTime = Instant.parse("2026-04-06T00:00:00Z");
        PlayerStateCache.CachedPlayerState snapshot = PlayerStateSnapshotMapper.snapshot(sourceSession, snapshotTime);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.cachedAt()).isEqualTo(snapshotTime);
        assertThat(snapshot.inventoryItemIds()).containsExactly("iron_sword");
        assertThat(snapshot.completedQuests()).containsExactly("quest_intro");
        assertThat(snapshot.activeQuests()).hasSize(1);
        assertThat(snapshot.followingNpcIds()).containsExactly("npc_obi");
        assertThat(snapshot.activeConsumableEffects()).hasSize(1);

        WorldService targetWorld = mock(WorldService.class);
        when(targetWorld.getItemById("iron_sword")).thenReturn(sword);
        when(targetWorld.getRoom("tavern")).thenReturn(tavern);
        when(targetWorld.getNpcRoomId("npc_obi")).thenReturn("grove");

        GameSession restoredSession = new GameSession("s2", new Player("p2", "Guest", "start"), targetWorld);
        restoredSession.getPlayer().getQuestState().restoreCompletedQuest("stale_completed");
        restoredSession.getPlayer().getQuestState().restoreActiveQuest("stale_active", "old_objective", 1, 0);
        restoredSession.addFollower("stale_follower");

        PlayerStateSnapshotMapper.restore(restoredSession, snapshot);

        Player restoredPlayer = restoredSession.getPlayer();
        assertThat(restoredPlayer.getCurrentRoomId()).isEqualTo("tavern");
        assertThat(restoredPlayer.getLevel()).isEqualTo(7);
        assertThat(restoredPlayer.getTitle()).isEqualTo("Veteran");
        assertThat(restoredPlayer.getRace()).isEqualTo("Human");
        assertThat(restoredPlayer.getCharacterClass()).isEqualTo("Ashen Knight");
        assertThat(restoredPlayer.getDescription()).isEqualTo("A seasoned traveler.");
        assertThat(restoredPlayer.getHealth()).isEqualTo(82);
        assertThat(restoredPlayer.getMaxHealth()).isEqualTo(110);
        assertThat(restoredPlayer.getExperience()).isEqualTo(345);
        assertThat(restoredPlayer.getGold()).isEqualTo(19);
        assertThat(restoredPlayer.getRecallRoomId()).isEqualTo("town_square");
        assertThat(restoredPlayer.getInventory()).containsExactly(sword);
        assertThat(restoredPlayer.getEquippedWeaponId()).isEqualTo("iron_sword");
        assertThat(restoredPlayer.getQuestState().getCompletedQuests()).containsExactly("quest_intro");
        assertThat(restoredPlayer.getQuestState().getActiveQuestIds()).containsExactly("quest_wolves");
        assertThat(restoredPlayer.getQuestState().getActiveQuest("quest_wolves").getObjectiveProgress()).isEqualTo(2);
        assertThat(restoredPlayer.getQuestState().getActiveQuest("quest_wolves").getDialogueStage()).isEqualTo(1);
        assertThat(restoredSession.getFollowingNpcs()).containsExactly("npc_obi");
        assertThat(restoredSession.getActiveConsumableEffects()).hasSize(1);
        assertThat(restoredSession.getActiveConsumableEffects().getFirst().type()).isEqualTo(ConsumableEffectType.DAMAGE_OVER_TIME);
        verify(targetWorld).moveNpc("npc_obi", "grove", "tavern");
    }
}
