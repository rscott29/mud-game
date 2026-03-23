package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class TalkService {

    private static final String SENTIENT_SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/talk-sentient-system.txt");

    private final ChatClient chatClient;
    private final AiTextPolisher textPolisher;

    @Autowired
    public TalkService(ChatClient.Builder chatClientBuilder, AiTextPolisher textPolisher) {
        this(chatClientBuilder.build(), textPolisher);
    }

    TalkService(ChatClient chatClient) {
        this(chatClient, AiTextPolisher.noOp());
    }

    TalkService(ChatClient chatClient, AiTextPolisher textPolisher) {
        this.chatClient = chatClient;
        this.textPolisher = textPolisher == null ? AiTextPolisher.noOp() : textPolisher;
    }

    public String buildDialogue(GameSession session, Npc npc) {
        String playerName = session.getPlayer().getName();

        if (!npc.isSentient()) {
            return nonSentientDialogue(npc, playerName);
        }

        return sentientDialogue(npc, playerName);
    }

    private String nonSentientDialogue(Npc npc, String playerName) {
        List<String> templates = npc.getTalkTemplates();
        if (templates.isEmpty()) {
            return Messages.fmt("command.talk.npc_no_speech", "npc", npc.getName());
        }

        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        AiTextPolisher.Tone tone = npc.isHumorous()
                ? AiTextPolisher.Tone.PLAYFUL
                : AiTextPolisher.Tone.DEFAULT;
        return textPolisher.polish(template, AiTextPolisher.Style.NPC_DIALOGUE, tone)
                .replace("{name}", npc.getName())
                .replace("{player}", playerName);
    }

    private String sentientDialogue(Npc npc, String playerName) {
        String personalityLine = (npc.getPersonality() != null && !npc.getPersonality().isBlank())
                ? "Personality: " + npc.getPersonality()
                : "";
        String toneLine = npc.isHumorous()
                ? "Tone: Lightly funny, playful, and charming when appropriate."
                : "";
        String systemPrompt = Messages.fmtTemplate(SENTIENT_SYSTEM_PROMPT,
                "name", npc.getName(),
                "description", npc.getDescription(),
                "personality", personalityLine,
                "tone", toneLine);

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
