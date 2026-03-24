package com.scott.tech.mud.mud_game.auth;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.persistence.entity.AccountEntity;
import com.scott.tech.mud.mud_game.persistence.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Manages player accounts: creation, password verification, and brute-force lockouts.
 *
 * Persists data via JPA to the {@code accounts} table (Flyway V1 migration).
 * All mutation methods are {@link Transactional} - Hibernate's dirty-checking
 * flushes field changes to the database at the end of each transaction without
 * needing an explicit {@code save()} call on managed entities.
 */
@Service
@Transactional
public class AccountStore {

    private static final Logger log = LoggerFactory.getLogger(AccountStore.class);

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AccountStore(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    private int getMaxAttempts() {
        try {
            return Integer.parseInt(Messages.get("config.auth.account.max-attempts"));
        } catch (NumberFormatException e) {
            log.warn("Failed to parse max attempts, using default 5", e);
            return 5;
        }
    }

    private Duration getLockDuration() {
        try {
            long minutes = Long.parseLong(Messages.get("config.auth.account.lock-duration-minutes"));
            return Duration.ofMinutes(minutes);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse lock duration, using default 5 minutes", e);
            return Duration.ofMinutes(5);
        }
    }

    // -- Query -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public boolean exists(String username) {
        return accountRepository.existsById(username.toLowerCase());
    }

    /**
     * Returns true if the account is currently locked.
     * Automatically clears an expired lock - the entity change is flushed by
     * the enclosing transaction.
     */
    public boolean isLocked(String username) {
        return accountRepository.findById(username.toLowerCase())
                .map(account -> {
                    if (account.getLockedUntil() == null) return false;
                    if (Instant.now().isBefore(account.getLockedUntil())) return true;
                    // Lock expired - clear it eagerly
                    account.setLockedUntil(null);
                    account.setFailedAttempts(0);
                    return false;
                })
                .orElse(false);
    }

    /** Seconds remaining on the lock, or 0 if not locked / account not found. */
    @Transactional(readOnly = true)
    public long lockRemainingSeconds(String username) {
        return accountRepository.findById(username.toLowerCase())
                .filter(a -> a.getLockedUntil() != null)
                .map(a -> Math.max(0, Duration.between(Instant.now(), a.getLockedUntil()).getSeconds()))
                .orElse(0L);
    }

    /** How many password attempts remain before the account locks. */
    @Transactional(readOnly = true)
    public int getRemainingAttempts(String username) {
        return accountRepository.findById(username.toLowerCase())
                .map(a -> Math.max(0, getMaxAttempts() - a.getFailedAttempts()))
                .orElse(getMaxAttempts());
    }

    /** Returns true if the account has god (admin) privileges. */
    @Transactional(readOnly = true)
    public boolean isGod(String username) {
        return accountRepository.findById(username.toLowerCase())
                .map(AccountEntity::isGod)
                .orElse(false);
    }

    /** Returns true if the account has moderator privileges. */
    @Transactional(readOnly = true)
    public boolean isModerator(String username) {
        return accountRepository.findById(username.toLowerCase())
                .map(AccountEntity::isModerator)
                .orElse(false);
    }

    // -- Mutation --------------------------------------------------------------

    /**
     * Verifies the raw password against the stored BCrypt hash.
     * On success, resets failed-attempt counters.
     * On failure, increments the counter and locks the account if the threshold is reached.
     *
     * @return true if the password is correct
     */
    public boolean verifyPassword(String username, String rawPassword) {
        return accountRepository.findById(username.toLowerCase())
                .map(account -> {
                    if (encoder.matches(rawPassword, account.getPasswordHash())) {
                        account.setFailedAttempts(0);
                        account.setLockedUntil(null);
                        return true;
                    }
                    int attempts = account.getFailedAttempts() + 1;
                    account.setFailedAttempts(attempts);
                    if (attempts >= getMaxAttempts()) {
                        account.setLockedUntil(Instant.now().plus(getLockDuration()));
                        log.warn("Account '{}' locked until {} after {} failed attempts.",
                                username, account.getLockedUntil(), attempts);
                    }
                    return false;
                })
                .orElse(false);
    }

    /** Temporarily locks an account (e.g., after being kicked). Account will auto-unlock after lock duration. */
    public void lockTemporarily(String username) {
        accountRepository.findById(username.toLowerCase())
                .ifPresent(account -> {
                    account.setLockedUntil(Instant.now().plus(getLockDuration()));
                    log.info("Account '{}' locked until {} (kicked).", username, account.getLockedUntil());
                });
    }

    /** Grants or revokes moderator privileges for an account. */
    public void setModerator(String username, boolean moderator) {
        accountRepository.findById(username.toLowerCase())
                .ifPresent(account -> {
                    account.setModerator(moderator);
                    log.info("Account '{}' moderator status set to {}", username.toLowerCase(), moderator);
                });
    }

    /** Creates a brand-new account and persists it. */
    public void createAccount(String username, String rawPassword) {
        AccountEntity account = new AccountEntity(
                username.toLowerCase(),
                encoder.encode(rawPassword),
                Instant.now()
        );
        accountRepository.save(account);
        log.info("New account created: '{}'", username.toLowerCase());
    }
}
