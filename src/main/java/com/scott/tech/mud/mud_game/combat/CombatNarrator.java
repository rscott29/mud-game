package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CombatNarrator {

    /** Damage qualifier definition: threshold, CSS class, player verb, NPC verb. */
    private record DamageQualifier(int maxDamage, String cssClass, String playerVerb, String npcVerb) {}

    private static final List<DamageQualifier> QUALIFIERS = List.of(
            new DamageQualifier(0, "qualifier-miss", "miss", "misses"),
            new DamageQualifier(2, "qualifier-scratch", "scratch", "scratches"),
            new DamageQualifier(4, "qualifier-graze", "graze", "grazes"),
            new DamageQualifier(6, "qualifier-hit", "hit", "hits"),
            new DamageQualifier(10, "qualifier-injure", "injure", "injures"),
            new DamageQualifier(14, "qualifier-wound", "wound", "wounds"),
            new DamageQualifier(18, "qualifier-maul", "maul", "mauls"),
            new DamageQualifier(22, "qualifier-decimate", "decimate", "decimates"),
            new DamageQualifier(26, "qualifier-devastate", "devastate", "devastates"),
            new DamageQualifier(30, "qualifier-maim", "maim", "maims"),
            new DamageQualifier(34, "qualifier-mutilate", "mutilate", "mutilates"),
            new DamageQualifier(38, "qualifier-disembowel", "disembowel", "disembowels"),
            new DamageQualifier(42, "qualifier-massacre", "massacre", "massacres"),
            new DamageQualifier(46, "qualifier-obliterate", "obliterate", "obliterates"),
            new DamageQualifier(Integer.MAX_VALUE, "qualifier-annihilate", "ANNIHILATE", "ANNIHILATES")
    );

    public String targetAlreadyDead(Npc npc) {
        return Messages.fmt("combat.target_already_dead", "npc", npc.getName());
    }

    public String playerMiss(Player player, PlayerCombatStats stats, Npc target) {
        return Messages.fmt("combat.player_misses",
                "actorLabel", "Your",
                "npc", target.getName(),
                "action", playerMissAction(stats));
    }

    public String playerMissForParty(Player player, PlayerCombatStats stats, Npc target) {
        return Messages.fmt("combat.party.player_misses",
                "actorLabel", possessive(player.getName()),
                "npc", target.getName(),
                "action", playerMissAction(stats));
    }

    public String playerHit(Player player, PlayerCombatStats stats, Npc target, int damage) {
        return Messages.fmt("combat.player_hits",
                "actorLabel", "Your",
                "npc", target.getName(),
                "action", playerAction(stats, damage));
    }

    public String playerHitForParty(Player player, PlayerCombatStats stats, Npc target, int damage) {
        return Messages.fmt("combat.party.player_hits",
                "actorLabel", possessive(player.getName()),
                "npc", target.getName(),
                "action", playerAction(stats, damage));
    }

    public String npcHit(Npc attacker, Player player, int damage) {
        return Messages.fmt("combat.npc_hits",
                "npc", attacker.getName(),
                "player", player.getName(),
                "action", npcAction(damage));
    }

    public String npcHitForParty(Npc attacker, Player player, int damage) {
        return Messages.fmt("combat.party.npc_hits",
                "npc", attacker.getName(),
                "player", player.getName(),
                "action", npcAction(damage));
    }

    public String npcHealth(CombatEncounter encounter) {
        Npc target = encounter.getTarget();
        return Messages.fmt("combat.npc_health",
                "npc", target.getName(),
                "health", String.valueOf(encounter.getCurrentHealth()),
                "maxHealth", String.valueOf(target.getMaxHealth()),
                "healthPercent", String.valueOf(healthPercent(encounter.getCurrentHealth(), target.getMaxHealth())));
    }

    public String npcDefeated(Npc target) {
        return Messages.fmt("combat.npc_defeated", "npc", target.getName());
    }

    public String playerDefeated() {
        return Messages.get("combat.player_defeated");
    }

    public String playerDefeated(Player player) {
        return Messages.fmt("combat.party.player_defeated", "player", player.getName());
    }

    public String playerHealth(Player player) {
        return Messages.fmt("combat.player_health",
                "health", String.valueOf(player.getHealth()),
                "maxHealth", String.valueOf(player.getMaxHealth()),
                "healthPercent", String.valueOf(healthPercent(player.getHealth(), player.getMaxHealth())));
    }

    public String xpGained(int xp) {
        return Messages.fmt("combat.xp_gained", "xp", String.valueOf(xp));
    }

    public String npcRespawns(Npc target) {
        return Messages.fmt("combat.npc_respawns", "npc", target.getName());
    }

    private String playerAction(PlayerCombatStats stats, int damage) {
        DamageQualifier qualifier = qualifierFor(damage);
        if (stats.attackVerb() != null) {
            return weaponVerbSpan(stats) + " <span class='" + qualifier.cssClass() + "'>" + qualifier.npcVerb() + "</span>";
        }
        return "<span class='" + qualifier.cssClass() + "'>" + qualifier.npcVerb() + "</span>";
    }

    private String playerMissAction(PlayerCombatStats stats) {
        if (stats.attackVerb() != null) {
            return weaponVerbSpan(stats) + " <span class='qualifier-miss'>misses</span>";
        }
        return "<span class='qualifier-miss'>misses</span>";
    }

    private String weaponVerbSpan(PlayerCombatStats stats) {
        String rarityClass = stats.weaponRarity() == null || stats.weaponRarity().isBlank()
                ? ""
                : " weapon-verb--" + stats.weaponRarity();
        return "<span class='weapon-verb" + rarityClass + "'>" + stats.attackVerb() + "</span>";
    }

    private String npcAction(int damage) {
        DamageQualifier qualifier = qualifierFor(damage);
        return "<span class='" + qualifier.cssClass() + "'>" + qualifier.npcVerb() + "</span>";
    }

    private int healthPercent(int currentHealth, int maxHealth) {
        if (maxHealth <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100, (int) Math.round((currentHealth * 100.0) / maxHealth)));
    }

    private String possessive(String name) {
        return name.endsWith("s") ? name + "'" : name + "'s";
    }

    private DamageQualifier qualifierFor(int damage) {
        return QUALIFIERS.stream()
                .filter(qualifier -> damage <= qualifier.maxDamage())
                .findFirst()
                .orElse(QUALIFIERS.getLast());
    }
}
