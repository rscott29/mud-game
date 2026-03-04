package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles player attempts to talk to / interact with an NPC.
 *
 * Two modes based on the NPC's {@code sentient} flag:
 * <ul>
 *   <li><b>Non-sentient</b> (animals, statues, etc.) — picks a random entry from
 *       the NPC's {@code talkTemplates}. No AI call is made.</li>
 *   <li><b>Sentient</b> — fires an AI call with the NPC's description and optional
 *       personality as the system context, producing an in-character spoken line.</li>
 * </ul>
 */
public class TalkCommand implements GameCommand {

    /** Prepositions/articles the AI may erroneously include as the first arg token. */
    private static final java.util.regex.Pattern LEADING_STOP_WORDS =
        java.util.regex.Pattern.compile("^(to|at|with|the|a|an)\\s+", java.util.regex.Pattern.CASE_INSENSITIVE);

    private static final String SENTIENT_SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/talk-sentient-system.txt");

    private final String target;
    private final ChatClient chatClient;

    /** Strips all leading stop-word prefixes (loops until stable). */
    private static String stripStopWords(String s) {
        String prev;
        do {
            prev = s;
            s = LEADING_STOP_WORDS.matcher(s).replaceFirst("");
        } while (!s.equals(prev));
        return s;
    }

    public TalkCommand(String target, ChatClient chatClient) {
        if (target == null || target.isBlank()) {
            this.target = null;
        } else {
            String stripped = stripStopWords(target.trim());
            this.target = stripped.isBlank() ? null : stripped.toLowerCase();
        }
        this.chatClient = chatClient;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.talk.no_target")));
        }

        Room room = session.getCurrentRoom();
        Optional<Npc> match = room.findNpcByKeyword(target);

        if (match.isEmpty()) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.talk.npc_not_found", "target", target)));
        }

        Npc npc = match.get();

        // ── Non-sentient path ────────────────────────────────────────────────
        if (!npc.isSentient()) {
            List<String> templates = npc.getTalkTemplates();
            if (templates.isEmpty()) {
                return CommandResult.of(GameResponse.message(
                    Messages.fmt("command.talk.npc_no_speech", "npc", npc.getName())
                ));
            }
            String msg = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
            return CommandResult.of(GameResponse.message(
                msg.replace("{name}", npc.getName())
                   .replace("{player}", session.getPlayer().getName())
            ));
        }

        // ── Sentient path – AI-generated in-character response ───────────────
        String personalityLine = (npc.getPersonality() != null && !npc.getPersonality().isBlank())
                ? "Personality: " + npc.getPersonality()
                : "";
        String systemPrompt = Messages.fmtTemplate(SENTIENT_SYSTEM_PROMPT,
                "name", npc.getName(), "description", npc.getDescription(), "personality", personalityLine);

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(Messages.fmt("command.talk.sentient_user_message", "player", session.getPlayer().getName()))
                    .call()
                    .content();
            return CommandResult.of(GameResponse.message(
                npc.getName() + ": \"" + response.trim() + "\""
            ));
        } catch (Exception e) {
            return CommandResult.of(GameResponse.message(
                Messages.fmt("command.talk.npc_speech_failed", "npc", npc.getName())
            ));
        }
    }
}
