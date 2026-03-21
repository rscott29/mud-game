package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TalkService {

    private static final String SENTIENT_SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/talk-sentient-system.txt");

    private final ChatClient chatClient;

    @Autowired
    public TalkService(ChatClient.Builder chatClientBuilder) {
        this(chatClientBuilder.build());
    }

    TalkService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Build dialogue text for talking to an NPC.
     * Returns the raw dialogue string (for embedding in room updates).
     */
    public String buildDialogue(GameSession session, Npc npc) {
        String playerName = session.getPlayer().getName();

        if (!npc.isSentient()) {
            return nonSentientDialogue(npc, playerName);
        }

        return sentientDialogue(npc, playerName);
    }

    /**
     * @deprecated Use buildDialogue() instead for consistent room-based output.
     */
    @Deprecated
    public GameResponse buildResponse(GameSession session, Npc npc) {
        return GameResponse.narrative(buildDialogue(session, npc));
    }

    private String nonSentientDialogue(Npc npc, String playerName) {
        List<String> templates = npc.getTalkTemplates();
        if (templates.isEmpty()) {
            return Messages.fmt("command.talk.npc_no_speech", "npc", npc.getName());
        }

        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        return template
                .replace("{name}", npc.getName())
                .replace("{player}", playerName);
    }

    private String sentientDialogue(Npc npc, String playerName) {
        String personalityLine = (npc.getPersonality() != null && !npc.getPersonality().isBlank())
                ? "Personality: " + npc.getPersonality()
                : "";
        String systemPrompt = Messages.fmtTemplate(SENTIENT_SYSTEM_PROMPT,
                "name", npc.getName(),
                "description", npc.getDescription(),
                "personality", personalityLine);

        try {
            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(Messages.fmt("command.talk.sentient_user_message", "player", playerName))
                    .call()
                    .content();
            return npc.getName() + ": \"" + response.trim() + "\"";
        } catch (Exception e) {
            return Messages.fmt("command.talk.npc_speech_failed", "npc", npc.getName());
        }
    }
}
