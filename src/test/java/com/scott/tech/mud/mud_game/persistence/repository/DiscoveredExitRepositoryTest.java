package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.persistence.entity.AccountEntity;
import com.scott.tech.mud.mud_game.persistence.entity.DiscoveredExitEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DiscoveredExitRepositoryTest {

    @Autowired
    private DiscoveredExitRepository repository;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void seedAccounts() {
        accountRepository.save(new AccountEntity("alice", "x", Instant.parse("2025-01-01T00:00:00Z")));
        accountRepository.save(new AccountEntity("bob", "x", Instant.parse("2025-01-01T00:00:00Z")));
    }

    @Test
    void findAllByUsername_returnsOnlyMatchingRows() {
        repository.save(new DiscoveredExitEntity("alice", "room_1", Direction.NORTH));
        repository.save(new DiscoveredExitEntity("alice", "room_1", Direction.EAST));
        repository.save(new DiscoveredExitEntity("alice", "room_2", Direction.SOUTH));
        repository.save(new DiscoveredExitEntity("bob", "room_1", Direction.NORTH));

        List<DiscoveredExitEntity> aliceExits = repository.findAllByUsername("alice");

        assertThat(aliceExits)
                .hasSize(3)
                .allSatisfy(exit -> assertThat(exit.getUsername()).isEqualTo("alice"));
    }

    @Test
    void findAllByUsername_returnsEmptyWhenNoMatches() {
        assertThat(repository.findAllByUsername("nobody")).isEmpty();
    }
}
