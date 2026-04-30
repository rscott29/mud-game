package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Player;
import org.springframework.stereotype.Component;

/**
 * Pure helpers that mutate a {@link Player}'s vital stats and return a localized
 * description of what changed (or {@code null} if there was no effect — e.g. trying
 * to heal a player already at full health).
 *
 * <p>No I/O, no broadcasting, no persistence — just the math on the player.</p>
 */
@Component
final class PlayerStatModifier {

    String restoreHealth(Player player, int amount) {
        int restored = Math.min(amount, Math.max(0, player.getMaxHealth() - player.getHealth()));
        if (restored <= 0) {
            return null;
        }
        player.setHealth(player.getHealth() + restored);
        return Messages.fmt("consumable.effect.restore_health", "amount", String.valueOf(restored));
    }

    String restoreMana(Player player, int amount) {
        int restored = Math.min(amount, Math.max(0, player.getMaxMana() - player.getMana()));
        if (restored <= 0) {
            return null;
        }
        player.setMana(player.getMana() + restored);
        return Messages.fmt("consumable.effect.restore_mana", "amount", String.valueOf(restored));
    }

    String restoreMovement(Player player, int amount) {
        int restored = Math.min(amount, Math.max(0, player.getMaxMovement() - player.getMovement()));
        if (restored <= 0) {
            return null;
        }
        player.setMovement(player.getMovement() + restored);
        return Messages.fmt("consumable.effect.restore_movement", "amount", String.valueOf(restored));
    }

    String damageHealth(Player player, int amount, String sourceName) {
        int appliedDamage = Math.min(amount, Math.max(0, player.getHealth()));
        if (appliedDamage <= 0) {
            return null;
        }
        player.setHealth(Math.max(0, player.getHealth() - appliedDamage));
        String source = sourceName == null || sourceName.isBlank() ? "It" : sourceName;
        return Messages.fmt(
                "consumable.effect.damage_health",
                "source", source,
                "amount", String.valueOf(appliedDamage)
        );
    }
}
