package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository for {@link AccountEntity}.
 * The primary key (username) is a String.
 */
@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, String> {
}
