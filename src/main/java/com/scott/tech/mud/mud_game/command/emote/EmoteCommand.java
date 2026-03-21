package com.scott.tech.mud.mud_game.command.emote;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Pronouns;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

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

    private final String emoteText;
    private final GameSessionManager sessionManager;

    public EmoteCommand(String emoteText, GameSessionManager sessionManager) {
        this.emoteText = emoteText == null ? "" : emoteText.trim();
        this.sessionManager = sessionManager;
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

        String normalizedEmote = normalize(emoteText);

        // Self-target handling first
        Matcher selfMatcher = wholeWordMatcher(playerName, normalizedEmote);
        if (selfMatcher.find()) {
            String othersMessage = playerName + " " + selfMatcher.replaceFirst(pronouns.reflexive());
            String selfMessage = "You " + selfMatcher.replaceFirst("yourself");

            return CommandResult.withAction(
                    RoomAction.inCurrentRoom(othersMessage),
                    GameResponse.narrative(selfMessage)
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
                String messageForOthers = playerName + " " + matcher.replaceFirst(targetName);
                String messageForTarget = playerName + " " + matcher.replaceFirst("you");
                String messageForSelf = "You " + matcher.replaceFirst(targetName);

                return CommandResult.withAction(
                        RoomAction.withTarget(
                                messageForOthers,
                                other.getSessionId(),
                                messageForTarget
                        ),
                        GameResponse.narrative(messageForSelf)
                );
            }
        }

        // No target found - normal room emote
        String roomMessage = playerName + " " + normalizedEmote;
        String selfMessage = "You " + normalizedEmote;

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(roomMessage),
                GameResponse.narrative(selfMessage)
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
}