package com.scott.tech.mud.mud_game.command.shop;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;
import com.scott.tech.mud.mud_game.session.GameSession;

public class ShopCommand implements GameCommand {

    private final ShopService shopService;

    public ShopCommand(ShopService shopService) {
        this.shopService = shopService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Room room = session.getCurrentRoom();
        Shop shop = shopService.getShop(room);
        if (shop == null) {
            return CommandResult.of(GameResponse.error(Messages.get("command.shop.no_shop")));
        }

        String merchant = room.getNpcs().stream()
                .filter(npc -> shop.getMerchantNpcId().equals(npc.getId()))
                .map(Npc::getName)
                .findFirst()
                .orElse(shop.getMerchantNpcId());

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.shop.browse", "player", session.getPlayer().getName())),
                GameResponse.roomRefresh(room, Messages.fmt("command.shop.browse", "merchant", merchant))
        );
    }
}