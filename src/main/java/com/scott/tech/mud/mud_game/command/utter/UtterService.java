package com.scott.tech.mud.mud_game.command.utter;

import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.config.SkillTableService.SkillDefinition;
import com.scott.tech.mud.mud_game.magic.MagicCastingService;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UtterService {

    public static final String WHISPER_OF_DOUBT_ID = "whisper-of-doubt";
    public static final String WHISPER_OF_DOUBT_NAME = "Whisper of Doubt";
    public static final String TRACE_THE_ECHO_ID = "trace-the-echo";
    public static final String TRACE_THE_ECHO_NAME = "Trace the Echo";
    public static final String LINGERING_PHRASE_ID = "lingering-phrase";
    public static final String LINGERING_PHRASE_NAME = "Lingering Phrase";
    public static final String NAME_THE_WOUND_ID = "name-the-wound";
    public static final String NAME_THE_WOUND_NAME = "Name the Wound";
    public static final String SEVER_THE_NAME_ID = "sever-the-name";
    public static final String SEVER_THE_NAME_NAME = "Sever the Name";
    public static final String HUSH_ID = "hush";
    public static final String HUSH_NAME = "Hush";

    private static final String WHISPERBINDER_CLASS_ID = "whisperbinder";

    private final MagicCastingService magicCastingService;

    public UtterService(MagicCastingService magicCastingService) {
        this.magicCastingService = magicCastingService;
    }

    public ParsedUtter parse(List<String> args) {
        MagicCastingService.ParsedMagicSkill parsedSkill = magicCastingService.parse(
                WHISPERBINDER_CLASS_ID,
                WHISPER_OF_DOUBT_ID,
                args
        );
        return new ParsedUtter(parsedSkill.skill(), parsedSkill.target());
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

    public record ParsedUtter(SkillDefinition skill, String target) {
    }
}
