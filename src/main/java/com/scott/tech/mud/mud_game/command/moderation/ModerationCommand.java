package com.scott.tech.mud.mud_game.command.moderation;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.ModerationPreferences;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Lets moderators configure the broadcast moderation policy for the whole world.
 */
public class ModerationCommand implements GameCommand {

    private final String input;
    private final WorldModerationPolicyService moderationPolicyService;

    public ModerationCommand(String input, WorldModerationPolicyService moderationPolicyService) {
        this.input = input == null ? "" : input.trim();
        this.moderationPolicyService = moderationPolicyService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        ModerationPreferences policy = moderationPolicyService.currentPolicy();

        if (!session.getPlayer().canManageModeration()) {
            if (input.isBlank() || input.equalsIgnoreCase("show")
                    || input.equalsIgnoreCase("status") || input.equalsIgnoreCase("list")) {
                return CommandResult.of(GameResponse.narrative(renderStatus(
                        policy,
                        "This world's moderation policy is managed by moderators.",
                        false
                )));
            }
            return CommandResult.of(GameResponse.error(Messages.get("command.moderation.not_moderator")));
        }

        if (input.isBlank()) {
            return CommandResult.of(GameResponse.narrative(renderStatus(policy, null, true)));
        }

        String[] parts = input.split("\\s+");
        String action = parts[0].toLowerCase(Locale.ROOT);
        if (action.equals("show") || action.equals("status") || action.equals("list")) {
            return CommandResult.of(GameResponse.narrative(renderStatus(policy, null, true)));
        }

        if (!action.equals("allow") && !action.equals("block")) {
            return CommandResult.of(GameResponse.error(Messages.get("command.moderation.usage")));
        }
        if (parts.length < 2) {
            return CommandResult.of(GameResponse.error(Messages.get("command.moderation.usage")));
        }

        String target = input.substring(action.length()).trim();
        ModerationPreferences updatedPolicy;
        if (target.equalsIgnoreCase("all")) {
            if (action.equals("allow")) {
                updatedPolicy = moderationPolicyService.allowAll();
            } else {
                updatedPolicy = moderationPolicyService.blockAll();
            }
            return CommandResult.of(GameResponse.narrative(renderStatus(
                    updatedPolicy,
                    action.equals("allow")
                            ? "This world now allows all configurable categories in player broadcasts."
                            : "This world now blocks all configurable categories in player broadcasts.",
                    true
            )));
        }

        ModerationCategory category = ModerationCategory.fromId(target)
                .filter(ModerationCategory::userSelectable)
                .orElse(null);
        if (category == null) {
            return CommandResult.of(GameResponse.error(Messages.fmt(
                    "command.moderation.invalid_category",
                    "category", target,
                    "categories", validCategories()
            )));
        }

        if (action.equals("allow")) {
            updatedPolicy = moderationPolicyService.allow(category);
        } else {
            updatedPolicy = moderationPolicyService.block(category);
        }

        String summary = action.equals("allow")
                ? "This world now allows " + category.displayName() + " in player broadcasts."
                : "This world now blocks " + category.displayName() + " in player broadcasts.";
        return CommandResult.of(GameResponse.narrative(renderStatus(updatedPolicy, summary, true)));
    }

    private static String renderStatus(ModerationPreferences policy, String summary, boolean canEdit) {
        StringBuilder html = new StringBuilder();
        html.append("<strong>World Broadcast Moderation</strong>");
        if (summary != null && !summary.isBlank()) {
            html.append("<br><br>").append(summary);
        }
        html.append("<br><br>");

        for (ModerationCategory category : ModerationCategory.configurableValues()) {
            html.append("- ")
                    .append(category.displayName())
                    .append(": <strong>")
                    .append(policy.blocks(category) ? "blocked" : "allowed")
                    .append("</strong><br>");
        }

        if (canEdit) {
            html.append("<br>Use <code>moderation allow &lt;category&gt;</code> or ")
                    .append("<code>moderation block &lt;category&gt;</code> to change the world policy.");
        } else {
            html.append("<br>Only moderators and gods can change the world policy.");
        }
        html.append("<br>Categories: <code>")
                .append(validCategories())
                .append("</code>");
        return html.toString();
    }

    private static String validCategories() {
        return ModerationCategory.configurableValues().stream()
                .map(ModerationCategory::commandToken)
                .collect(Collectors.joining(", ")) + ", all";
    }
}
