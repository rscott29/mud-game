package com.scott.tech.mud.mud_game.command.follow;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class FollowCommand implements GameCommand {

    private static final Set<String> STOP_WORDS = Set.of("stop", "off", "none", "leave", "unfollow");

    private final String rawTarget;
    private final GameSessionManager sessionManager;
    private final PartyService partyService;

    public FollowCommand(String rawTarget, GameSessionManager sessionManager, PartyService partyService) {
        this.rawTarget = rawTarget == null ? "" : rawTarget.trim();
        this.sessionManager = sessionManager;
        this.partyService = partyService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (rawTarget.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.follow.usage")));
        }

        String normalizedTarget = rawTarget.toLowerCase(Locale.ROOT);
        if (STOP_WORDS.contains(normalizedTarget)) {
            boolean stopped = partyService.stopFollowing(session.getSessionId());
            if (!stopped) {
                return CommandResult.of(GameResponse.narrative(Messages.get("command.follow.not_following")));
            }

            return CommandResult.withAction(
                    RoomAction.inCurrentRoom(Messages.fmt(
                            "action.follow.stop",
                            "player", session.getPlayer().getName()
                    )),
                    GameResponse.narrative(Messages.get("command.follow.stop"))
            );
        }

        Optional<GameSession> targetSession = sessionManager.findPlayingByNameInRoom(
                rawTarget,
                session.getPlayer().getCurrentRoomId()
        );
        if (targetSession.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.follow.not_found", "player", rawTarget)));
        }

        GameSession target = targetSession.get();
        PartyService.FollowOutcome outcome = partyService.follow(session, target);
        return switch (outcome) {
            case CANNOT_FOLLOW_SELF -> CommandResult.of(GameResponse.error(Messages.get("command.follow.self")));
            case CYCLE_DETECTED -> CommandResult.of(GameResponse.error(Messages.get("command.follow.cycle")));
            case ALREADY_FOLLOWING -> CommandResult.of(GameResponse.narrative(
                    Messages.fmt("command.follow.already", "player", target.getPlayer().getName())));
            case FOLLOWING -> CommandResult.withAction(
                    RoomAction.inCurrentRoom(Messages.fmt(
                            "action.follow",
                            "player", session.getPlayer().getName(),
                            "leader", target.getPlayer().getName()
                    )),
                    GameResponse.narrative(Messages.fmt(
                            "command.follow.success",
                            "player", target.getPlayer().getName(),
                            "pronounSubject", capitalize(target.getPlayer().getPronounsSubject()),
                            "pronounBe", beVerbFor(target.getPlayer().getPronounsSubject())
                    ))
            );
        };
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "They";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String beVerbFor(String subjectPronoun) {
        if (subjectPronoun == null) {
            return "are";
        }

        return switch (subjectPronoun.trim().toLowerCase(Locale.ROOT)) {
            case "they", "we", "you" -> "are";
            default -> "is";
        };
    }
}