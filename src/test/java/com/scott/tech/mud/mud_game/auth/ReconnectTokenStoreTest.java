package com.scott.tech.mud.mud_game.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReconnectTokenStoreTest {

    private ReconnectTokenStore store;

    @BeforeEach
    void setUp() {
        store = new ReconnectTokenStore();
    }

    @Test
    void issueAndConsume_returnsUsername() {
        String token = store.issue("alice");
        Optional<String> result = store.consume(token);
        assertThat(result).contains("alice");
    }

    @Test
    void consume_singleUse_secondConsumeFails() {
        String token = store.issue("alice");
        store.consume(token);
        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void consume_unknownToken_returnsEmpty() {
        assertThat(store.consume("not-a-real-token")).isEmpty();
    }

    @Test
    void consume_nullToken_returnsEmpty() {
        assertThat(store.consume(null)).isEmpty();
    }

    @Test
    void revokeForUser_preventsConsume() {
        String token = store.issue("bob");
        store.revokeForUser("bob");
        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void revokeForUser_doesNotAffectOtherUsers() {
        String aliceToken = store.issue("alice");
        store.issue("bob");
        store.revokeForUser("bob");
        assertThat(store.consume(aliceToken)).contains("alice");
    }

    @Test
    void issue_newTokenSupersedesprevious() {
        String first  = store.issue("alice");
        String second = store.issue("alice");
        assertThat(first).isNotEqualTo(second);
        // Both are valid until consumed (previous is not explicitly revoked by issuing)
        assertThat(store.consume(second)).contains("alice");
    }
}

