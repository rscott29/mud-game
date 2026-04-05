package com.scott.tech.mud.mud_game.command.shop;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Set;

public class BuyCommand implements GameCommand {

    private final String target;
    private final ShopService shopService;
    private final ExperienceTableService xpTables;

    public BuyCommand(String target, ShopService shopService, ExperienceTableService xpTables) {
        this.target = target;
        this.shopService = shopService;
        this.xpTables = xpTables;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Room room = session.getCurrentRoom();
        Shop shop = shopService.getShop(room);
        if (shop == null) {
            return CommandResult.of(GameResponse.error(Messages.get("command.shop.no_shop")));
        }

        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.shop.buy.no_target")));
        }

        Shop.Listing listing = shop.findListing(target).orElse(null);
        if (listing == null) {
            String available = shop.getListings().stream()
                    .map(candidate -> candidate.item().getName())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.shop.buy.not_found", "item", target)
                            + (available.isBlank() ? "" : " " + Messages.fmt("command.shop.buy.available", "items", available))
            ));
        }

        if (shopService.alreadyOwns(session, listing.item())) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.shop.buy.already_carrying", "item", listing.item().getName())
            ));
        }

        if (!shopService.canAfford(session, listing)) {
            Player player = session.getPlayer();
            int shortfall = Math.max(0, listing.price() - player.getGold());
            return CommandResult.of(GameResponse.error(Messages.fmt(
                    "command.shop.buy.not_enough_gold",
                    "item", listing.item().getName(),
                    "price", String.valueOf(listing.price()),
                    "gold", String.valueOf(player.getGold()),
                    "shortfall", String.valueOf(shortfall)
            )));
        }

        shopService.buy(session, listing);

        Player player = session.getPlayer();
        String merchant = merchantName(room, shop);
        List<GameResponse.ItemView> views = player.getInventory().stream()
                .map(item -> GameResponse.ItemView.from(item, player))
                .toList();

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt(
                        "action.shop.buy",
                        "player", player.getName(),
                        "item", listing.item().getName(),
                        "merchant", merchant
                )),
                GameResponse.inventoryUpdate(views).withPlayerStats(player, xpTables),
                GameResponse.roomRefresh(room, Messages.fmt(
                        "command.shop.buy.success",
                        "item", listing.item().getName(),
                        "price", String.valueOf(listing.price()),
                        "merchant", merchant
                ), List.of(), session.getDiscoveredHiddenExits(room.getId()), Set.of(), true)
        );
    }

    private String merchantName(Room room, Shop shop) {
        return room.getNpcs().stream()
                .filter(npc -> shop.getMerchantNpcId().equals(npc.getId()))
                .map(Npc::getName)
                .findFirst()
                .orElse(shop.getMerchantNpcId());
    }
}
