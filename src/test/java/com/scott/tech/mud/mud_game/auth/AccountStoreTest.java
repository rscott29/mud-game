package com.scott.tech.mud.mud_game.auth;

import com.scott.tech.mud.mud_game.persistence.entity.AccountEntity;
import com.scott.tech.mud.mud_game.persistence.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountStoreTest {

    private AccountRepository accountRepository;
    private AccountStore accountStore;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        accountStore = new AccountStore(accountRepository);
        encoder = new BCryptPasswordEncoder();
    }

    @Test
    void exists_lowercasesUsername() {
        when(accountRepository.existsById("alice")).thenReturn(true);

        boolean exists = accountStore.exists("Alice");

        assertThat(exists).isTrue();
        verify(accountRepository).existsById("alice");
    }

    @Test
    void createAccount_savesLowercasedUsernameWithBcryptHash() {
        accountStore.createAccount("Alice", "secret");

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountRepository).save(captor.capture());
        AccountEntity saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret");
        assertThat(saved.getPasswordHash()).startsWith("$2");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void verifyPassword_success_resetsFailedAttemptsAndLock() {
        AccountEntity account = new AccountEntity("alice", encoder.encode("secret"), Instant.now());
        account.setFailedAttempts(3);
        account.setLockedUntil(Instant.now().plusSeconds(120));
        when(accountRepository.findById("alice")).thenReturn(Optional.of(account));

        boolean ok = accountStore.verifyPassword("alice", "secret");

        assertThat(ok).isTrue();
        assertThat(account.getFailedAttempts()).isZero();
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void verifyPassword_failureAtThreshold_setsLock() {
        AccountEntity account = new AccountEntity("alice", encoder.encode("secret"), Instant.now());
        account.setFailedAttempts(4);
        when(accountRepository.findById("alice")).thenReturn(Optional.of(account));

        boolean ok = accountStore.verifyPassword("alice", "wrong");

        assertThat(ok).isFalse();
        assertThat(account.getFailedAttempts()).isEqualTo(5);
        assertThat(account.getLockedUntil()).isNotNull();
        assertThat(account.getLockedUntil()).isAfter(Instant.now().minusSeconds(1));
    }

    @Test
    void isLocked_whenLockInFuture_returnsTrue() {
        AccountEntity account = new AccountEntity("alice", encoder.encode("secret"), Instant.now());
        account.setLockedUntil(Instant.now().plusSeconds(60));
        account.setFailedAttempts(2);
        when(accountRepository.findById("alice")).thenReturn(Optional.of(account));

        boolean locked = accountStore.isLocked("alice");

        assertThat(locked).isTrue();
        assertThat(account.getFailedAttempts()).isEqualTo(2);
    }

    @Test
    void isLocked_whenLockExpired_clearsLockAndFailedAttempts() {
        AccountEntity account = new AccountEntity("alice", encoder.encode("secret"), Instant.now());
        account.setLockedUntil(Instant.now().minusSeconds(1));
        account.setFailedAttempts(4);
        when(accountRepository.findById("alice")).thenReturn(Optional.of(account));

        boolean locked = accountStore.isLocked("alice");

        assertThat(locked).isFalse();
        assertThat(account.getLockedUntil()).isNull();
        assertThat(account.getFailedAttempts()).isZero();
    }

    @Test
    void getRemainingAttempts_accountsForFailedAttemptCount() {
        AccountEntity account = new AccountEntity("alice", encoder.encode("secret"), Instant.now());
        account.setFailedAttempts(2);
        when(accountRepository.findById("alice")).thenReturn(Optional.of(account));

        int remaining = accountStore.getRemainingAttempts("alice");

        assertThat(remaining).isEqualTo(3);
    }

    @Test
    void lockRemainingSeconds_missingAccount_returnsZero() {
        when(accountRepository.findById("alice")).thenReturn(Optional.empty());

        long remaining = accountStore.lockRemainingSeconds("alice");

        assertThat(remaining).isZero();
    }
}
