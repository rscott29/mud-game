package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.PersistedCorpseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PersistedCorpseRepository extends JpaRepository<PersistedCorpseEntity, String> {

    List<PersistedCorpseEntity> findAllByExpiresAtAfterOrderByCreatedAtAsc(Instant instant);

    List<PersistedCorpseEntity> findAllByExpiresAtLessThanEqual(Instant instant);
}
