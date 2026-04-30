package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.PersistedCorpseEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistedCorpseRepositoryTest {

    @Autowired
    private PersistedCorpseRepository repository;

    @Test
    void findAllByExpiresAtAfter_returnsOnlyLiveCorpsesInOrder() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        repository.save(corpse("c1", now.minusSeconds(60), now.plusSeconds(300)));
        repository.save(corpse("c2", now.minusSeconds(120), now.plusSeconds(600)));
        repository.save(corpse("c3-expired", now.minusSeconds(900), now.minusSeconds(60)));

        List<PersistedCorpseEntity> live = repository.findAllByExpiresAtAfterOrderByCreatedAtAsc(now);

        assertThat(live)
                .extracting(PersistedCorpseEntity::getCorpseId)
                .containsExactly("c2", "c1");
    }

    @Test
    void findAllByExpiresAtLessThanEqual_returnsOnlyExpiredCorpses() {
        Instant now = Instant.parse("2025-01-01T12:00:00Z");
        repository.save(corpse("alive", now.minusSeconds(60), now.plusSeconds(300)));
        repository.save(corpse("expired1", now.minusSeconds(900), now.minusSeconds(60)));
        repository.save(corpse("expired2", now.minusSeconds(900), now));

        List<PersistedCorpseEntity> expired = repository.findAllByExpiresAtLessThanEqual(now);

        assertThat(expired)
                .extracting(PersistedCorpseEntity::getCorpseId)
                .containsExactlyInAnyOrder("expired1", "expired2");
    }

    private static PersistedCorpseEntity corpse(String id, Instant created, Instant expires) {
        return new PersistedCorpseEntity(id, "room_1", "alice", "", created, expires);
    }
}
