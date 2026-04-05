package com.scott.tech.mud.mud_game.quest;

/**
 * Shared quest UI fragments for command responses.
 */
public final class QuestPresentation {

    private QuestPresentation() {
    }

    public static boolean isUnderleveled(Quest quest, int playerLevel) {
        return isUnderleveled(quest.recommendedLevel(), playerLevel);
    }

    public static boolean isUnderleveled(int recommendedLevel, int playerLevel) {
        return playerLevel < Math.max(1, recommendedLevel);
    }

    public static String buildMetaBadges(Quest quest, int playerLevel) {
        return buildMetaBadges(quest.challengeRating(), quest.recommendedLevel(), playerLevel);
    }

    public static String buildMetaBadges(QuestChallengeRating challengeRating,
                                         int recommendedLevel,
                                         int playerLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='quest-meta'>");
        sb.append("<span class='quest-badge quest-badge--challenge quest-badge--")
                .append(challengeRating.cssModifier())
                .append("'>")
                .append(challengeRating.badgeText())
                .append("</span>");
        sb.append("<span class='quest-badge quest-badge--level'>Recommended Lv. ")
                .append(Math.max(1, recommendedLevel))
                .append("</span>");
        if (isUnderleveled(recommendedLevel, playerLevel)) {
            sb.append("<span class='quest-badge quest-badge--underleveled'>You are Lv. ")
                    .append(Math.max(1, playerLevel))
                    .append("</span>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    public static String buildUnderlevelWarning(Quest quest, int playerLevel) {
        if (!isUnderleveled(quest, playerLevel)) {
            return "";
        }

        return "<div class='quest-warning'>This quest is rated <strong>"
                + quest.challengeRating().badgeText()
                + "</strong> and is tuned for around <strong>level "
                + quest.recommendedLevel()
                + "</strong>. You can still attempt it at <strong>level "
                + Math.max(1, playerLevel)
                + "</strong>, but success will likely require strong gear and careful play.</div>";
    }
}
