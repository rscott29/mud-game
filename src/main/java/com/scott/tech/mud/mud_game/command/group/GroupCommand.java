package com.scott.tech.mud.mud_game.command.group;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class GroupCommand implements GameCommand {

    private final PartyService partyService;
    private final GameSessionManager sessionManager;
    private final CombatState combatState;

    public GroupCommand(PartyService partyService, GameSessionManager sessionManager, CombatState combatState) {
        this.partyService = partyService;
        this.sessionManager = sessionManager;
        this.combatState = combatState;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!partyService.isInGroup(session.getSessionId())) {
            return CommandResult.of(GameResponse.narrative(Messages.get("command.group.none")));
        }

        String leaderSessionId = partyService.resolveLeaderSessionId(session.getSessionId());
        List<GameSession> members = new ArrayList<>(partyService.getPartySessions(session.getSessionId(), sessionManager));
        members.sort(Comparator
                .comparing((GameSession member) -> !member.getSessionId().equals(leaderSessionId))
                .thenComparing(member -> member.getPlayer().getName(), String.CASE_INSENSITIVE_ORDER));

        GameSession leader = members.stream()
                .filter(member -> member.getSessionId().equals(leaderSessionId))
                .findFirst()
                .orElse(session);

        String memberLines = members.stream()
                .map(member -> formatMemberLine(member, leaderSessionId, session.getSessionId()))
                .reduce((left, right) -> left + "<br>" + right)
                .orElse(Messages.get("command.group.none"));

        return CommandResult.of(GameResponse.narrative(Messages.fmt(
                "command.group.summary",
                "leader", leader.getPlayer().getName(),
                "count", String.valueOf(members.size()),
                "members", memberLines
        )));
    }

    private String formatMemberLine(GameSession member, String leaderSessionId, String viewerSessionId) {
        List<String> tags = new ArrayList<>();
        if (member.getSessionId().equals(leaderSessionId)) {
            tags.add(Messages.get("command.group.tag.leader"));
        }
        if (member.getSessionId().equals(viewerSessionId)) {
            tags.add(Messages.get("command.group.tag.you"));
        }

        String annotations = tags.isEmpty() ? "" : " (" + String.join(", ", tags) + ")";
        String roomName = member.getCurrentRoom() != null
                ? member.getCurrentRoom().getName()
                : member.getPlayer().getCurrentRoomId();
        String status = combatState.getCombatTarget(member.getSessionId())
                .map(npc -> Messages.fmt("command.group.member_target", "target", npc.getName()))
                .orElse("");

        return Messages.fmt(
                "command.group.member_line",
                "name", member.getPlayer().getName(),
                "annotations", annotations,
                "room", roomName,
                "status", status
        );
    }
}