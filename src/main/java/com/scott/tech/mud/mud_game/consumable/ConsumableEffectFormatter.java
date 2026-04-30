package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.config.Messages;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure presentation helpers for consumable use:
 * <ul>
 *   <li>resolves the verb-specific message keys (drink/quaff/eat/consume/use)</li>
 *   <li>builds the player-facing "use" message including effect descriptions</li>
 *   <li>renders effect names/descriptions with positive/negative tone styling</li>
 * </ul>
 *
 * <p>Static-only — no Spring bean. Holds no state and depends on nothing but
 * {@link Messages}.</p>
 */
final class ConsumableEffectFormatter {

    record ConsumePresentation(String commandMessageKey, String actionMessageKey) {
    }

    private ConsumableEffectFormatter() {
    }

    static ConsumePresentation consumePresentation(String verb, boolean consumeItem) {
        String normalizedVerb = verb == null || verb.isBlank()
                ? "use"
                : verb.trim().toLowerCase(Locale.ROOT);

        return switch (normalizedVerb) {
            case "drink" -> consumeItem
                    ? new ConsumePresentation("command.use.success.drink", "action.use.drink")
                    : new ConsumePresentation("command.use.success.drink.from", "action.use.drink.from");
            case "quaff" -> consumeItem
                    ? new ConsumePresentation("command.use.success.quaff", "action.use.quaff")
                    : new ConsumePresentation("command.use.success.quaff.from", "action.use.quaff.from");
            case "eat" -> new ConsumePresentation("command.use.success.eat", "action.use.eat");
            case "consume" -> new ConsumePresentation("command.use.success.consume", "action.use.consume");
            default -> new ConsumePresentation("command.use.success", "action.use");
        };
    }

    static String buildUseMessage(String itemName, List<String> effectMessages, ConsumePresentation presentation) {
        String baseMessage = Messages.fmt(presentation.commandMessageKey(), "item", itemName);
        if (effectMessages == null || effectMessages.isEmpty()) {
            return baseMessage + " " + Messages.get("command.use.no_obvious_effect");
        }
        return baseMessage + "<br><br>" + String.join("<br>", effectMessages);
    }

    static void addIfPresent(List<String> messages, String message) {
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }

    static void addEffectMessage(List<String> messages, ConsumableEffect effect, String mechanicMessage) {
        addIfPresent(messages, formatEffectDescription(effect));
        addIfPresent(messages, mechanicMessage);
    }

    static String formatEffectDescription(ConsumableEffect effect) {
        if (effect == null) {
            return null;
        }

        String description = effect.description();
        String nameMarkup = formatEffectName(effect);
        if (description == null || description.isBlank()) {
            return nameMarkup;
        }
        if (nameMarkup == null) {
            return description;
        }

        String effectName = effect.name();
        Matcher matcher = Pattern.compile(Pattern.quote(effectName), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(description);
        if (!matcher.find()) {
            return nameMarkup + ". " + description;
        }

        return description.substring(0, matcher.start())
                + "<span class='"
                + effectToneClass(effect)
                + "'>"
                + description.substring(matcher.start(), matcher.end())
                + "</span>"
                + description.substring(matcher.end());
    }

    private static String formatEffectName(ConsumableEffect effect) {
        if (effect == null || effect.name() == null || effect.name().isBlank()) {
            return null;
        }
        return "<span class='" + effectToneClass(effect) + "'>" + effect.name() + "</span>";
    }

    private static String effectToneClass(ConsumableEffect effect) {
        return effect.type() != null && effect.type().isBeneficial()
                ? "term-effect term-effect--positive"
                : "term-effect term-effect--negative";
    }
}
