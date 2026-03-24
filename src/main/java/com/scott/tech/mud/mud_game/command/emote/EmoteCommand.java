package com.scott.tech.mud.mud_game.command.emote;

import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.ModerationPreferences;
import com.scott.tech.mud.mud_game.model.Pronouns;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom emote command that broadcasts a player's action to the room.
 *
 * Examples:
 *   /em smiles
 *      You see:    "You smile"
 *      Others see: "Rich smiles"
 *
 *   /em waves at Bob
 *      You see:    "You wave at Bob"
 *      Bob sees:   "Rich waves at you"
 *      Others see: "Rich waves at Bob"
 *
 *   /em pats Rich on the head
 *      You see:    "You pat yourself on the head"
 *      Others see: "Rich pats themselves on the head" (uses player's pronouns)
 */
public class EmoteCommand implements GameCommand {

    static final String SELF_TOKEN = "<<SELF>>";
    static final String TARGET_TOKEN = "<<TARGET>>";

    private final String emoteText;
    private final GameSessionManager sessionManager;
    private final EmotePerspectiveResolver perspectiveResolver;
    private final PlayerTextModerator textModerator;
    private final WorldModerationPolicyService moderationPolicyService;

    public EmoteCommand(String emoteText,
                        GameSessionManager sessionManager,
                        EmotePerspectiveResolver perspectiveResolver) {
        this(emoteText, sessionManager, perspectiveResolver, PlayerTextModerator.noOp(), null);
    }

    public EmoteCommand(String emoteText,
                        GameSessionManager sessionManager,
                        EmotePerspectiveResolver perspectiveResolver,
                        PlayerTextModerator textModerator,
                        WorldModerationPolicyService moderationPolicyService) {
        this.emoteText = emoteText == null ? "" : emoteText.trim();
        this.sessionManager = sessionManager;
        this.perspectiveResolver = perspectiveResolver;
        this.textModerator = textModerator == null ? PlayerTextModerator.noOp() : textModerator;
        this.moderationPolicyService = moderationPolicyService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (emoteText.isBlank()) {
            return CommandResult.of(
                    GameResponse.error(Messages.get("command.emote.usage"))
            );
        }

        String playerName = session.getPlayer().getName();
        String roomId = session.getPlayer().getCurrentRoomId();
        Pronouns pronouns = session.getPlayer().getPronouns();
        String reflexive = pronouns == null || pronouns.reflexive() == null || pronouns.reflexive().isBlank()
                ? "themselves"
                : pronouns.reflexive();

        String normalizedEmote = normalize(emoteText);
        PlayerTextModerator.Review review = textModerator.review(normalizedEmote);
        if (blocks(review.category())) {
            return CommandResult.of(GameResponse.moderationNotice(blockedMessage(review.category())));
        }

        // Self-target handling first
        Matcher selfMatcher = wholeWordMatcher(playerName, normalizedEmote);
        if (selfMatcher.find()) {
            EmotePerspectiveResolver.Perspective perspective =
                    perspectiveResolver.resolve(selfMatcher.replaceFirst(SELF_TOKEN));
            String othersMessage = playerName + " " + perspective.thirdPerson().replace(SELF_TOKEN, reflexive);
            String selfMessage = "You " + perspective.secondPerson().replace(SELF_TOKEN, "yourself");

            return CommandResult.withAction(
                    RoomAction.inCurrentRoom(othersMessage, GameResponse.Type.SOCIAL_ACTION),
                    GameResponse.socialAction(selfMessage)
            );
        }

        // Target another player in the same room
        for (GameSession other : sessionManager.getSessionsInRoom(roomId)) {
            if (other.getSessionId().equals(session.getSessionId())) {
                continue;
            }

            String targetName = other.getPlayer().getName();
            Matcher matcher = wholeWordMatcher(targetName, normalizedEmote);

            if (matcher.find()) {
                EmotePerspectiveResolver.Perspective perspective =
                        perspectiveResolver.resolve(matcher.replaceFirst(TARGET_TOKEN));
                String messageForOthers = playerName + " " + perspective.thirdPerson().replace(TARGET_TOKEN, targetName);
                String messageForTarget = playerName + " " + perspective.thirdPerson().replace(TARGET_TOKEN, "you");
                String messageForSelf = "You " + perspective.secondPerson().replace(TARGET_TOKEN, targetName);

                return CommandResult.withAction(
                        RoomAction.withTarget(
                                messageForOthers,
                                other.getSessionId(),
                                messageForTarget,
                                GameResponse.Type.SOCIAL_ACTION
                        ),
                        GameResponse.socialAction(messageForSelf)
                );
            }
        }

        // No target found - normal room emote
        EmotePerspectiveResolver.Perspective perspective = perspectiveResolver.resolve(normalizedEmote);
        String roomMessage = playerName + " " + perspective.thirdPerson();
        String selfMessage = "You " + perspective.secondPerson();

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(roomMessage, GameResponse.Type.SOCIAL_ACTION),
                GameResponse.socialAction(selfMessage)
        );
    }

    private static Matcher wholeWordMatcher(String name, String text) {
        Pattern pattern = Pattern.compile(
                "\\b" + Pattern.quote(name) + "\\b",
                Pattern.CASE_INSENSITIVE
        );
        return pattern.matcher(text);
    }

    /**
     * Small cleanup so emotes read more naturally.
     */
    private static String normalize(String text) {
        String result = text.trim();

        // Collapse repeated spaces
        result = result.replaceAll("\\s+", " ");

        // Remove leading punctuation that looks odd after a name
        result = result.replaceFirst("^[,.;:!?-]+", "").trim();

        return result;
    }

    private static String blockedMessage(ModerationCategory category) {
        if (category != null && category.userSelectable()) {
            return Messages.fmt(
                    "command.moderation.blocked.category",
                    "category", category.displayName(),
                    "command", category.commandToken()
            );
        }
        return Messages.get("command.moderation.blocked");
    }

    private boolean blocks(ModerationCategory category) {
        if (moderationPolicyService == null) {
            return ModerationPreferences.defaults().blocks(category);
        }
        return moderationPolicyService.blocks(category);
    }
}
