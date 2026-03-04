package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.PlayerProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link PlayerProfileEntity}.
 */
@Repository
public interface PlayerProfileRepository extends JpaRepository<PlayerProfileEntity, String> {
}
