package com.scott.tech.mud.mud_game.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReconnectTokenStoreTest {

    @Test
    void issue_supersedesOlderTokensForSameUser() {
        ReconnectTokenStore store = new ReconnectTokenStore();

        String firstToken = store.issue("Alice");
        String secondToken = store.issue("alice");

        assertThat(store.consume(firstToken)).isEmpty();
        assertThat(store.consume(secondToken)).contains("alice");
    }

    @Test
    void revokeForUser_isCaseInsensitive() {
        ReconnectTokenStore store = new ReconnectTokenStore();

        String aliceToken = store.issue("Alice");
        String bobToken = store.issue("Bob");

        store.revokeForUser("ALICE");

        assertThat(store.consume(aliceToken)).isEmpty();
        assertThat(store.consume(bobToken)).contains("bob");
    }

    @Test
    void resolve_doesNotConsumeToken() {
        ReconnectTokenStore store = new ReconnectTokenStore();

        String token = store.issue("Alice");

        assertThat(store.resolve(token)).contains("alice");
        assertThat(store.consume(token)).contains("alice");
    }
}
