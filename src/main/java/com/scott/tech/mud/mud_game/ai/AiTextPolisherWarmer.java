package com.scott.tech.mud.mud_game.ai;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.service.AmbientEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pre-populates {@link AiTextPolisher}'s cache for templates that fire on the
 * common request path (every player movement, ambient room events) so the
 * first player to trigger them does not pay the AI round-trip latency.
 *
 * <p>Runs once after application startup, on a background thread, only when
 * {@code app.ai.warm-cache=true}. Templates that fail polishing fall back to
 * the original text and are silently skipped — same behaviour as a normal
 * call site.</p>
 *
 * <p>NPC {@code interactTemplates} are intentionally not warmed: there are
 * dozens of them, most never seen by any given player, and each fires only
 * when a specific room is entered. Lazy population on first encounter is the
 * better trade-off for those.</p>
 */
@Component
@ConditionalOnProperty(name = "app.ai.warm-cache", havingValue = "true")
public class AiTextPolisherWarmer {

    private static final Logger log = LoggerFactory.getLogger(AiTextPolisherWarmer.class);

    /**
     * Templates polished on every player move. Style and tone match the call
     * sites in {@code MoveService} so cache keys line up exactly.
     */
    private static final List<WarmEntry> WARM_ENTRIES = List.of(
            new WarmEntry("command.move.departure",          AiTextPolisher.Style.ROOM_EVENT, AiTextPolisher.Tone.DEFAULT),
            new WarmEntry("command.move.arrival",            AiTextPolisher.Style.ROOM_EVENT, AiTextPolisher.Tone.DEFAULT),
            new WarmEntry("command.move.follower_departure", AiTextPolisher.Style.ROOM_EVENT, AiTextPolisher.Tone.DEFAULT)
    );

    private final AiTextPolisher polisher;
    private final AmbientEventService ambientEventService;

    public AiTextPolisherWarmer(AiTextPolisher polisher, AmbientEventService ambientEventService) {
        this.polisher = polisher;
        this.ambientEventService = ambientEventService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void warmCache() {
        long start = System.currentTimeMillis();
        int moveWarmed = 0;
        int moveFailed = 0;
        for (WarmEntry entry : WARM_ENTRIES) {
            String text = Messages.get(entry.messageKey());
            if (text == null || text.isBlank()) {
                continue;
            }
            try {
                polisher.polish(text, entry.style(), entry.tone());
                moveWarmed++;
                // Small delay to avoid hitting Groq's 6000 TPM rate limit
                Thread.sleep(200);
            } catch (Exception e) {
                moveFailed++;
                log.warn("Failed to warm move template '{}': {}", entry.messageKey(), e.getMessage());
            }
        }

        int ambientWarmed = ambientEventService.warmAiCache();

        if (moveFailed > 0) {
            log.warn("AI text polisher cache warmed: {} move templates, {} ambient lines in {} ms ({} move templates failed)",
                    moveWarmed, ambientWarmed, System.currentTimeMillis() - start, moveFailed);
        } else {
            log.info("AI text polisher cache warmed: {} move templates, {} ambient lines in {} ms",
                    moveWarmed, ambientWarmed, System.currentTimeMillis() - start);
        }
    }

    private record WarmEntry(String messageKey, AiTextPolisher.Style style, AiTextPolisher.Tone tone) {
    }
}
