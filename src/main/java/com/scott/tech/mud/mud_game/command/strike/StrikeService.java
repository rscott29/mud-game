package com.scott.tech.mud.mud_game.command.strike;

import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.config.SkillTableService.SkillDefinition;
import com.scott.tech.mud.mud_game.magic.MagicCastingService;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrikeService {

    public static final String CINDER_STRIKE_ID = "cinder-strike";
    public static final String CINDER_STRIKE_NAME = "Cinder Strike";
    public static final String ASHEN_GUARD_ID = "ashen-guard";
    public static final String ASHEN_GUARD_NAME = "Ashen Guard";
    public static final String EMBER_MARK_ID = "ember-mark";
    public static final String EMBER_MARK_NAME = "Ember Mark";
    public static final String SMOTHERING_BLOW_ID = "smothering-blow";
    public static final String SMOTHERING_BLOW_NAME = "Smothering Blow";
    public static final String KINDLE_PAIN_ID = "kindle-pain";
    public static final String KINDLE_PAIN_NAME = "Kindle Pain";

    private static final String ASHEN_KNIGHT_CLASS_ID = "ashen-knight";

    private final MagicCastingService magicCastingService;

    public StrikeService(MagicCastingService magicCastingService) {
        this.magicCastingService = magicCastingService;
    }

    public ParsedStrike parse(List<String> args) {
        MagicCastingService.ParsedMagicSkill parsedSkill = magicCastingService.parse(
                ASHEN_KNIGHT_CLASS_ID,
                CINDER_STRIKE_ID,
                args
        );
        return new ParsedStrike(parsedSkill.skill(), parsedSkill.target());
    }

    public boolean isUnlocked(Player player, SkillDefinition skill) {
        return magicCastingService.isUnlocked(player, skill);
    }

    public int manaCost(SkillDefinition skill) {
        return magicCastingService.manaCost(skill);
    }

    public CombatService.AttackResult cast(GameSession session, CombatEncounter encounter, SkillDefinition skill) {
        return magicCastingService.cast(session, encounter, skill);
    }

    public record ParsedStrike(SkillDefinition skill, String target) {
    }
}
