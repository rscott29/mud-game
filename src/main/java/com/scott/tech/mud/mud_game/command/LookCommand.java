package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Describes the player's current room, or examines a specific target.
 *
 * Usage:
 *   look              – full room description
 *   look <keyword>    – examine an NPC, item, or ask for exits
 *
 * Target resolution order:
 *   1. "exits" / "exit"  → list available exits
 *   2. NPC keyword match  → NPC description
 *   3. Item name match    → item description
 *   4. No match           → helpful error
 */
public class LookCommand implements GameCommand {

    /** Prepositions/articles the AI may erroneously include as the first arg token. */
    private static final java.util.regex.Pattern LEADING_STOP_WORDS =
        java.util.regex.Pattern.compile("^(at|the|a|an|towards?)\\s+", java.util.regex.Pattern.CASE_INSENSITIVE);

    /** null or blank = full room look */
    private final String target;
    private final GameSessionManager sessionManager;

    public LookCommand(String target, GameSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        if (target == null || target.isBlank()) {
            this.target = null;
        } else {
            String s = target.trim();
            String prev;
            do {
                prev = s;
                s = LEADING_STOP_WORDS.matcher(s).replaceFirst("");
            } while (!s.equals(prev));
            this.target = s.isBlank() ? null : s.toLowerCase();
        }
    }

    @Override
    public CommandResult execute(GameSession session) {
        Room room = session.getCurrentRoom();

        // No target – describe the whole room
        if (target == null) {
            List<String> others = othersInRoom(session);
            Set<Direction> discovered = session.getDiscoveredHiddenExits(room.getId());
            Set<String> invIds = session.getPlayer().getInventory().stream()
                    .map(Item::getId).collect(Collectors.toSet());
            return CommandResult.of(GameResponse.roomUpdate(room, Messages.get("command.look.around"), others, discovered, invIds));
        }

        // "exits" – list directions (include discovered hidden exits)
        if (target.equals("exits") || target.equals("exit")) {
            Set<Direction> discovered = session.getDiscoveredHiddenExits(room.getId());
            String exitList = Stream.concat(
                    room.getExits().keySet().stream(),
                    room.getHiddenExits().keySet().stream().filter(discovered::contains)
            ).map(d -> d.name().toLowerCase()).collect(Collectors.joining(", "));
            return CommandResult.of(GameResponse.message(
                exitList.isEmpty() ? Messages.get("command.look.no_exits") : Messages.fmt("command.look.exits", "exits", exitList)
            ));
        }

        // NPC keyword match
        Optional<Npc> npc = room.findNpcByKeyword(target);
        if (npc.isPresent()) {
            return CommandResult.of(GameResponse.message(
                npc.get().getName() + ": " + npc.get().getDescription()
            ));
        }

        // Item keyword match
        Optional<Item> item = room.findItemByKeyword(target);
        if (item.isPresent()) {
            return CommandResult.of(GameResponse.message(
                item.get().getName() + ": " + item.get().getDescription()
            ));
        }

        // Player name match (case-insensitive)
        Optional<GameSession> playerSession = sessionManager.getSessionsInRoom(room.getId())
                .stream()
                .filter(s -> !s.getSessionId().equals(session.getSessionId()))
                .filter(s -> s.getPlayer().getName().equalsIgnoreCase(target))
                .findFirst();
        if (playerSession.isPresent()) {
            var player = playerSession.get().getPlayer();
            StringBuilder desc = new StringBuilder();
            desc.append("<b>").append(player.getName()).append("</b>");
            
            // Add race and class
            String race = player.getRace();
            String characterClass = player.getCharacterClass();
            if (race != null || characterClass != null) {
                desc.append(" — ");
                if (race != null) desc.append(race);
                if (race != null && characterClass != null) desc.append(" ");
                if (characterClass != null) desc.append(characterClass);
            }
            
            // Add pronouns
            String pronouns = player.getPronounsSubject();
            if (pronouns != null) {
                desc.append(" (").append(pronouns).append("/").append(player.getPronounsObject()).append(")");
            }
            
            desc.append("\n");
            
            // Add description if available
            String playerDesc = player.getDescription();
            if (playerDesc != null && !playerDesc.isBlank()) {
                desc.append(playerDesc);
            } else {
                desc.append("A player standing here.");
            }
            
            return CommandResult.of(GameResponse.message(desc.toString()));
        }

        return CommandResult.of(GameResponse.error(
            Messages.fmt("command.look.not_found", "target", target)
        ));
    }

    /** Returns the names of all other PLAYING players in the same room. */
    private List<String> othersInRoom(GameSession self) {
        return sessionManager.getSessionsInRoom(self.getPlayer().getCurrentRoomId())
                .stream()
                .filter(s -> !s.getSessionId().equals(self.getSessionId()))
                .map(s -> s.getPlayer().getName())
                .collect(Collectors.toList());
    }
}
