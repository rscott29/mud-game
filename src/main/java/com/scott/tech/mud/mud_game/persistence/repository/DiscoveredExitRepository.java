package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.DiscoveredExitEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscoveredExitRepository
        extends JpaRepository<DiscoveredExitEntity, DiscoveredExitEntity.Key> {

    List<DiscoveredExitEntity> findAllByUsername(String username);
}
