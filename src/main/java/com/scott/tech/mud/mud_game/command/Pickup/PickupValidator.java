package com.scott.tech.mud.mud_game.command.Pickup;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.ItemTrigger;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PickupValidator {

    private static final String DEFAULT_PREREQUISITE_FAIL_MESSAGE =
            "Something holds you back. You feel unworthy of taking this.";

    public ValidationResult validate(GameSession session, Item item) {
        if (!item.isTakeable()) {
            return ValidationResult.deny(GameResponse.error(
                    Messages.fmt("command.pickup.not_takeable", "item", item.getName())));
        }

        if (isAlreadyCarried(session, item)) {
            return ValidationResult.deny(GameResponse.error("You're already carrying that."));
        }

        if (session.getPlayer().isGod()) {
            return ValidationResult.allow();
        }

        if (missingPrerequisites(session, item).isEmpty()) {
            return ValidationResult.allow();
        }

        return buildPrerequisiteFailure(session, item);
    }

    private boolean isAlreadyCarried(GameSession session, Item item) {
        return session.getPlayer().getInventory().stream()
                .anyMatch(i -> i.getId().equals(item.getId()));
    }

    private List<String> missingPrerequisites(GameSession session, Item item) {
        if (item.getRequiredItemIds() == null || item.getRequiredItemIds().isEmpty()) {
            return List.of();
        }

        Set<String> heldIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        return item.getRequiredItemIds().stream()
                .filter(requiredId -> !heldIds.contains(requiredId))
                .toList();
    }

    private ValidationResult buildPrerequisiteFailure(GameSession session, Item item) {
        List<GameResponse> responses = new ArrayList<>();
        responses.add(GameResponse.error(resolveFailMessage(item)));
        responses.addAll(buildPrerequisiteFailTriggerResponses(session, item));
        return ValidationResult.deny(responses);
    }

    private String resolveFailMessage(Item item) {
        String custom = item.getPrerequisiteFailMessage();
        return (custom == null || custom.isBlank())
                ? DEFAULT_PREREQUISITE_FAIL_MESSAGE
                : custom;
    }

    private List<GameResponse> buildPrerequisiteFailTriggerResponses(GameSession session, Item item) {
        if (item.getTriggers() == null || item.getTriggers().isEmpty()) {
            return List.of();
        }

        List<GameResponse> responses = new ArrayList<>();

        for (ItemTrigger trigger : item.getTriggers()) {
            if (trigger.getEvent() != ItemTrigger.Event.PREREQUISITE_FAIL) {
                continue;
            }

            findNpcInRoom(session, trigger.getNpcId())
                    .flatMap(npc -> resolveTemplate(npc, trigger.getTemplateIndex())
                            .map(template -> npc.getName() + ": \"" + interpolate(template, npc, session) + "\""))
                    .map(GameResponse::message)
                    .ifPresent(responses::add);
        }

        return responses;
    }

    private Optional<Npc> findNpcInRoom(GameSession session, String npcId) {
        return session.getCurrentRoom().getNpcs().stream()
                .filter(n -> n.getId().equals(npcId))
                .findFirst();
    }

    private Optional<String> resolveTemplate(Npc npc, int templateIndex) {
        List<String> templates = npc.getTalkTemplates();
        if (templates == null || templates.isEmpty()) {
            return Optional.empty();
        }

        if (templateIndex < 0) {
            return Optional.empty();
        }

        int safeIndex = Math.min(templateIndex, templates.size() - 1);
        return Optional.ofNullable(templates.get(safeIndex));
    }

    private String interpolate(String template, Npc npc, GameSession session) {
        return template
                .replace("{name}", npc.getName())
                .replace("{player}", session.getPlayer().getName());
    }
}