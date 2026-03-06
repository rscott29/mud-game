package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.InventoryItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InventoryItemRepository
        extends JpaRepository<InventoryItemEntity, InventoryItemEntity.InventoryItemId> {

    List<InventoryItemEntity> findByUsername(String username);

    void deleteByUsername(String username);
}
