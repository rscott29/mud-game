package com.scott.tech.mud.mud_game.persistence.repository;

import com.scott.tech.mud.mud_game.persistence.entity.WorldSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorldSettingRepository extends JpaRepository<WorldSettingEntity, String> {
}
