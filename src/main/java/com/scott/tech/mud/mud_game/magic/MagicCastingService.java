package com.scott.tech.mud.mud_game.magic;

import com.scott.tech.mud.mud_game.combat.CombatEffect;
import com.scott.tech.mud.mud_game.combat.CombatEffectType;
import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.WhisperbinderFragments;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.config.SkillTableService.ActiveMagicDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.CombatEffectDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.DamageProfile;
import com.scott.tech.mud.mud_game.config.SkillTableService.FragmentBuildDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.FragmentConsumeDamageDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.LowHealthBonusDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.ScaledValueDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.SkillDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.ThresholdDurationBonus;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;

@Service
public class MagicCastingService {

    private final SkillTableService skillTableService;
    private final CombatService combatService;
    private final IntSupplier fragmentRollSupplier;

    @Autowired
    public MagicCastingService(SkillTableService skillTableService, CombatService combatService) {
        this(skillTableService, combatService, () -> ThreadLocalRandom.current().nextInt(1, 101));
    }

    public MagicCastingService(SkillTableService skillTableService,
                               CombatService combatService,
                               IntSupplier fragmentRollSupplier) {
        this.skillTableService = skillTableService;
        this.combatService = combatService;
        this.fragmentRollSupplier = fragmentRollSupplier;
    }

    public ParsedMagicSkill parse(String characterClass, String defaultSkillId, List<String> args) {
        SkillDefinition defaultSkill = skillTableService.findSkill(characterClass, defaultSkillId)
                .filter(SkillDefinition::isActive)
                .orElseThrow(() -> new IllegalStateException("Missing active magic definition for skill '" + defaultSkillId + "'"));

        if (args == null || args.isEmpty()) {
            return new ParsedMagicSkill(defaultSkill, null);
        }

        String firstToken = normalizeAlias(args.getFirst());
        SkillDefinition matchedSkill = skillTableService.getActiveSkills(characterClass).stream()
                .filter(skill -> matchesAlias(skill, firstToken))
                .findFirst()
                .orElse(null);

        if (matchedSkill != null) {
            String target = args.size() > 1
                    ? stripArticle(String.join(" ", args.subList(1, args.size())))
                    : null;
            return new ParsedMagicSkill(matchedSkill, target);
        }

        return new ParsedMagicSkill(defaultSkill, stripArticle(String.join(" ", args)));
    }

    public boolean isUnlocked(Player player, SkillDefinition skill) {
        if (player == null || skill == null) {
            return false;
        }

        String skillId = normalizeValue(skill.id());
        return skillTableService.getUnlockedSkills(player).stream()
                .anyMatch(unlockedSkill -> normalizeValue(unlockedSkill.id()).equals(skillId));
    }

    public int manaCost(SkillDefinition skill) {
        return requireActiveMagic(skill).manaCost();
    }

    public CombatService.AttackResult cast(GameSession session, CombatEncounter encounter, SkillDefinition skill) {
        ActiveMagicDefinition activeMagic = requireActiveMagic(skill);
        CombatService.AttackResult result = activeMagic.directDamage() == null
                ? castNonDamageMagic(session, encounter, activeMagic)
                : castDirectDamageMagic(session, encounter, activeMagic);

        if (activeMagic.fragmentBuild() != null) {
            result = applyFragmentBuild(session, encounter, result, activeMagic.fragmentBuild());
        }
        return result;
    }

    private CombatService.AttackResult castDirectDamageMagic(GameSession session,
                                                             CombatEncounter encounter,
                                                             ActiveMagicDefinition activeMagic) {
        Player player = session.getPlayer();
        DamageProfile damageProfile = activeMagic.directDamage();
        int rawDamage = rollScaledDamage(player.getLevel(), damageProfile);
        rawDamage += fragmentDamageBonus(encounter, activeMagic.fragmentConsumeDamage());
        rawDamage += lowHealthDamageBonus(encounter, activeMagic.lowHealthBonus());

        CombatService.AttackResult result = combatService.executePlayerUtterance(
                session,
                encounter,
                rawDamage,
                () -> spendMana(player, activeMagic.manaCost()),
                actualDamage -> formatCastMessage(activeMagic.messageKeyPrefix() + ".player", player, encounter, actualDamage),
                actualDamage -> formatCastMessage(activeMagic.messageKeyPrefix() + ".party", player, encounter, actualDamage)
        );

        return finalizeFragmentConsumption(result, encounter, activeMagic.fragmentConsumeDamage());
    }

    private CombatService.AttackResult castNonDamageMagic(GameSession session,
                                                          CombatEncounter encounter,
                                                          ActiveMagicDefinition activeMagic) {
        Player player = session.getPlayer();
        spendMana(player, activeMagic.manaCost());

        boolean thresholdTriggered = applyConfiguredCombatEffect(session, encounter, activeMagic);

        CombatService.AttackResult result = CombatService.AttackResult.hit(
                formatCastMessage(activeMagic.messageKeyPrefix() + ".player", player, encounter, null),
                formatCastMessage(activeMagic.messageKeyPrefix() + ".party", player, encounter, null)
        );

        ThresholdDurationBonus thresholdDurationBonus = activeMagic.combatEffect() == null
                ? null
                : activeMagic.combatEffect().thresholdDurationBonus();
        if (thresholdTriggered && thresholdDurationBonus != null) {
            result = appendMessages(
                    result,
                    formatThresholdMessage(thresholdDurationBonus.playerMessageKey(), encounter),
                    formatThresholdMessage(thresholdDurationBonus.partyMessageKey(), encounter)
            );
        }
        return result;
    }

    private boolean applyConfiguredCombatEffect(GameSession session,
                                                CombatEncounter encounter,
                                                ActiveMagicDefinition activeMagic) {
        CombatEffectDefinition combatEffect = activeMagic.combatEffect();
        if (combatEffect == null) {
            if (activeMagic.threatOrDefault() > 0) {
                encounter.addThreat(session.getSessionId(), activeMagic.threatOrDefault());
            }
            return false;
        }

        int duration = Math.max(1, combatEffect.duration());
        boolean thresholdTriggered = false;
        ThresholdDurationBonus thresholdDurationBonus = combatEffect.thresholdDurationBonus();
        if (thresholdDurationBonus != null
                && WhisperbinderFragments.hasAtLeast(encounter, thresholdDurationBonus.fragmentThreshold())) {
            duration = Math.max(duration, thresholdDurationBonus.boostedDuration());
            thresholdTriggered = duration > combatEffect.duration();
        }

        int potency = resolvePotency(session.getPlayer(), combatEffect);
        encounter.applyEffect(new CombatEffect(
                resolveCombatEffectType(combatEffect.effectType()),
                session.getSessionId(),
                potency,
                duration
        ));
        if (activeMagic.threatOrDefault() > 0) {
            encounter.addThreat(session.getSessionId(), activeMagic.threatOrDefault());
        }
        return thresholdTriggered;
    }

    private int resolvePotency(Player player, CombatEffectDefinition combatEffect) {
        ScaledValueDefinition scaledPotency = combatEffect.scaledPotency();
        if (scaledPotency != null) {
            return scaleValue(player.getLevel(), scaledPotency);
        }
        return combatEffect.potency() == null ? 0 : Math.max(0, combatEffect.potency());
    }

    private int fragmentDamageBonus(CombatEncounter encounter,
                                    FragmentConsumeDamageDefinition fragmentConsumeDamage) {
        if (fragmentConsumeDamage == null) {
            return 0;
        }
        return WhisperbinderFragments.count(encounter) * Math.max(0, fragmentConsumeDamage.damagePerFragment());
    }

    private int lowHealthDamageBonus(CombatEncounter encounter,
                                     LowHealthBonusDefinition lowHealthBonus) {
        if (encounter == null || lowHealthBonus == null) {
            return 0;
        }

        return healthPercent(encounter) <= lowHealthBonus.thresholdPercent()
                ? Math.max(0, lowHealthBonus.bonusDamage())
                : 0;
    }

    private CombatService.AttackResult finalizeFragmentConsumption(CombatService.AttackResult result,
                                                                   CombatEncounter encounter,
                                                                   FragmentConsumeDamageDefinition fragmentConsumeDamage) {
        if (fragmentConsumeDamage == null || encounter == null) {
            return result;
        }

        int consumedFragments = WhisperbinderFragments.consumeAll(encounter);
        if (!result.targetDefeated() && consumedFragments > 0) {
            return appendFragmentStatus(result, encounter);
        }
        return result;
    }

    private CombatService.AttackResult applyFragmentBuild(GameSession session,
                                                          CombatEncounter encounter,
                                                          CombatService.AttackResult result,
                                                          FragmentBuildDefinition fragmentBuild) {
        if (result == null || result.targetDefeated() || encounter == null || !encounter.isAlive()) {
            return result;
        }

        int before = WhisperbinderFragments.count(encounter);
        if (before >= WhisperbinderFragments.MAX_FRAGMENTS) {
            return result;
        }

        int fragmentAttempts = fragmentBuild.attemptsForCurrentState(before);
        int chancePercent = Math.max(
            0,
            Math.min(100, fragmentBuild.chanceForLevel(session.getPlayer().getLevel())
                + session.getPlayer().getFragmentChanceBonusPercent())
        );
        int landedFragments = rollFragmentApplications(fragmentAttempts, chancePercent);
        if (landedFragments <= 0) {
            return appendMessages(
                    result,
                    Messages.fmt("utter.fragments.miss.player", "npc", encounter.getTarget().getName()),
                    Messages.fmt("utter.fragments.miss.party", "npc", encounter.getTarget().getName())
            );
        }

        int after = WhisperbinderFragments.add(encounter, session.getSessionId(), landedFragments);
        if (after == before) {
            return result;
        }
        return appendFragmentStatus(result, encounter);
    }

    private CombatService.AttackResult appendFragmentStatus(CombatService.AttackResult result,
                                                            CombatEncounter encounter) {
        int fragmentCount = WhisperbinderFragments.count(encounter);
        int stepBonus = WhisperbinderFragments.DAMAGE_BONUS_PER_FRAGMENT;
        return appendMessages(
                result,
                Messages.fmt(
                        "utter.fragments.player",
                        "npc", encounter.getTarget().getName(),
                        "state", WhisperbinderFragments.stateName(fragmentCount),
                        "count", String.valueOf(fragmentCount),
                        "max", String.valueOf(WhisperbinderFragments.MAX_FRAGMENTS),
                "stepBonus", String.valueOf(stepBonus),
                        "bonus", String.valueOf(WhisperbinderFragments.damageBonusPercent(fragmentCount))
                ),
                Messages.fmt(
                        "utter.fragments.party",
                        "npc", encounter.getTarget().getName(),
                        "state", WhisperbinderFragments.stateName(fragmentCount),
                        "count", String.valueOf(fragmentCount),
                        "max", String.valueOf(WhisperbinderFragments.MAX_FRAGMENTS),
                "stepBonus", String.valueOf(stepBonus),
                        "bonus", String.valueOf(WhisperbinderFragments.damageBonusPercent(fragmentCount))
                )
        );
    }

    private CombatService.AttackResult appendMessages(CombatService.AttackResult result,
                                                      String playerSuffix,
                                                      String partySuffix) {
        if (result == null) {
            return null;
        }

        return new CombatService.AttackResult(
                appendHtml(result.message(), playerSuffix),
                appendHtml(result.partyMessage(), partySuffix),
                result.targetDefeated(),
                result.playerDefeated(),
                result.encounterEnded(),
                result.xpGained(),
                result.questProgressResult()
        );
    }

    private String appendHtml(String message, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return message;
        }
        if (message == null || message.isBlank()) {
            return suffix;
        }
        return message + suffix;
    }

    private String formatCastMessage(String messageKey,
                                     Player player,
                                     CombatEncounter encounter,
                                     Integer actualDamage) {
        if (actualDamage == null) {
            return Messages.fmt(
                    messageKey,
                    "player", player.getName(),
                    "npc", encounter.getTarget().getName()
            );
        }
        return Messages.fmt(
                messageKey,
                "player", player.getName(),
                "npc", encounter.getTarget().getName(),
                "damage", String.valueOf(actualDamage)
        );
    }

    private String formatThresholdMessage(String messageKey, CombatEncounter encounter) {
        if (messageKey == null || messageKey.isBlank()) {
            return null;
        }
        return Messages.fmt(messageKey, "npc", encounter.getTarget().getName());
    }

    private void spendMana(Player player, int manaCost) {
        player.setMana(Math.max(0, player.getMana() - manaCost));
    }

    private int rollScaledDamage(int level, DamageProfile damageProfile) {
        int levelBonus = Math.max(0, (Math.max(level, 1) - 1) / Math.max(1, damageProfile.levelInterval()));
        int minDamage = damageProfile.minDamage() + levelBonus;
        int maxDamage = damageProfile.maxDamage() + levelBonus;
        return ThreadLocalRandom.current().nextInt(minDamage, maxDamage + 1);
    }

    private int scaleValue(int level, ScaledValueDefinition scaledValue) {
        return scaledValue.baseAmount() + Math.max(0, (Math.max(level, 1) - 1) / Math.max(1, scaledValue.levelInterval()));
    }

    private int rollFragmentApplications(int fragmentAttempts, int chancePercent) {
        int appliedFragments = 0;
        for (int attempt = 0; attempt < fragmentAttempts; attempt++) {
            if (rollFragmentProc(chancePercent)) {
                appliedFragments++;
            }
        }
        return appliedFragments;
    }

    private boolean rollFragmentProc(int chancePercent) {
        int normalizedChance = Math.max(0, Math.min(100, chancePercent));
        if (normalizedChance <= 0) {
            return false;
        }
        if (normalizedChance >= 100) {
            return true;
        }
        return fragmentRollSupplier.getAsInt() <= normalizedChance;
    }

    private int healthPercent(CombatEncounter encounter) {
        if (encounter.getTarget().getMaxHealth() <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(100,
                (int) Math.round((encounter.getCurrentHealth() * 100.0) / encounter.getTarget().getMaxHealth())));
    }

    private boolean matchesAlias(SkillDefinition skill, String normalizedAlias) {
        ActiveMagicDefinition activeMagic = skill.activeMagic();
        if (activeMagic == null) {
            return false;
        }
        if (normalizeAlias(skill.id()).equals(normalizedAlias)) {
            return true;
        }
        return activeMagic.aliases().stream()
                .map(this::normalizeAlias)
                .anyMatch(alias -> alias.equals(normalizedAlias));
    }

    private CombatEffectType resolveCombatEffectType(String effectType) {
        return CombatEffectType.valueOf(effectType.trim().toUpperCase(Locale.ROOT));
    }

    private ActiveMagicDefinition requireActiveMagic(SkillDefinition skill) {
        if (skill == null || skill.activeMagic() == null) {
            throw new IllegalStateException("Missing activeMagic configuration for skill '" + (skill == null ? "unknown" : skill.id()) + "'");
        }
        return skill.activeMagic();
    }

    private String normalizeAlias(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stripArticle(String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String article : List.of("a ", "an ", "the ")) {
            if (lower.startsWith(article)) {
                return trimmed.substring(article.length()).trim();
            }
        }
        return trimmed;
    }

    public record ParsedMagicSkill(SkillDefinition skill, String target) {
    }
}