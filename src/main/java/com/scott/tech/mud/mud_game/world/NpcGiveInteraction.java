package com.scott.tech.mud.mud_game.world;

import java.util.List;

/**
 * Data-driven NPC interaction triggered by giving an accepted item to an NPC.
 */
public record NpcGiveInteraction(
        List<String> acceptedItemIds,
        List<String> requiredItemIds,
        List<String> consumedItemIds,
        String rewardItemId,
        String denyIfPlayerHasItemId,
        int goldCost,
        List<String> alreadyOwnedDialogue,
        List<String> missingRequiredItemsDialogue,
        List<String> insufficientGoldDialogue,
        List<String> successDialogue,
        String missingRewardItemMessage
) {

    public NpcGiveInteraction {
        acceptedItemIds = safeList(acceptedItemIds);
        requiredItemIds = safeList(requiredItemIds);
        consumedItemIds = safeList(consumedItemIds);
        rewardItemId = blankToNull(rewardItemId);
        denyIfPlayerHasItemId = blankToNull(denyIfPlayerHasItemId);
        goldCost = Math.max(0, goldCost);
        alreadyOwnedDialogue = safeList(alreadyOwnedDialogue);
        missingRequiredItemsDialogue = safeList(missingRequiredItemsDialogue);
        insufficientGoldDialogue = safeList(insufficientGoldDialogue);
        successDialogue = safeList(successDialogue);
        missingRewardItemMessage = blankToNull(missingRewardItemMessage);
    }

    public boolean acceptsItem(String itemId) {
        return itemId != null && acceptedItemIds.contains(itemId);
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
