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

    public GameResponse buildResponse(GameSession session, Npc npc) {
        String playerName = session.getPlayer().getName();

        if (!npc.isSentient()) {
            return nonSentientResponse(npc, playerName);
        }

        return sentientResponse(npc, playerName);
    }

    private GameResponse nonSentientResponse(Npc npc, String playerName) {
        List<String> templates = npc.getTalkTemplates();
        if (templates.isEmpty()) {
            return GameResponse.message(Messages.fmt("command.talk.npc_no_speech", "npc", npc.getName()));
        }

        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        return GameResponse.message(template
                .replace("{name}", npc.getName())
                .replace("{player}", playerName));
    }

    private GameResponse sentientResponse(Npc npc, String playerName) {
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
            return GameResponse.message(npc.getName() + ": \"" + response.trim() + "\"");
        } catch (Exception e) {
            return GameResponse.message(Messages.fmt("command.talk.npc_speech_failed", "npc", npc.getName()));
        }
    }
}
