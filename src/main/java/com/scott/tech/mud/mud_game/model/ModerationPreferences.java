package com.scott.tech.mud.mud_game.model;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configurable moderation policy for player-authored broadcast text.
 */
public final class ModerationPreferences {

    private static final EnumSet<ModerationCategory> DEFAULT_BLOCKED = EnumSet.of(
            ModerationCategory.PROFANITY,
            ModerationCategory.SEXUAL_CONTENT,
            ModerationCategory.HATE_SPEECH,
            ModerationCategory.HARASSMENT
    );

    private final EnumSet<ModerationCategory> blockedCategories;

    private ModerationPreferences(EnumSet<ModerationCategory> blockedCategories) {
        this.blockedCategories = blockedCategories;
    }

    public static ModerationPreferences defaults() {
        return new ModerationPreferences(EnumSet.copyOf(DEFAULT_BLOCKED));
    }

    public static ModerationPreferences fromSerialized(String serialized) {
        if (serialized == null) {
            return defaults();
        }

        EnumSet<ModerationCategory> blocked = EnumSet.noneOf(ModerationCategory.class);
        for (String token : serialized.split(",")) {
            ModerationCategory.fromId(token)
                    .filter(ModerationCategory::userSelectable)
                    .ifPresent(blocked::add);
        }

        return new ModerationPreferences(blocked);
    }

    public static String defaultSerialized() {
        return defaults().serialize();
    }

    public boolean blocks(ModerationCategory category) {
        if (category == null || category == ModerationCategory.SAFE) {
            return false;
        }
        if (!category.userSelectable()) {
            return true;
        }
        return blockedCategories.contains(category);
    }

    public void allow(ModerationCategory category) {
        if (category != null && category.userSelectable()) {
            blockedCategories.remove(category);
        }
    }

    public void block(ModerationCategory category) {
        if (category != null && category.userSelectable()) {
            blockedCategories.add(category);
        }
    }

    public void allowAll() {
        blockedCategories.clear();
    }

    public void blockAll() {
        blockedCategories.clear();
        blockedCategories.addAll(DEFAULT_BLOCKED);
    }

    public Set<ModerationCategory> blockedCategories() {
        return Set.copyOf(blockedCategories);
    }

    public ModerationPreferences copy() {
        return new ModerationPreferences(EnumSet.copyOf(blockedCategories));
    }

    public String serialize() {
        return ModerationCategory.configurableValues().stream()
                .filter(blockedCategories::contains)
                .map(ModerationCategory::id)
                .collect(Collectors.joining(","));
    }
}
