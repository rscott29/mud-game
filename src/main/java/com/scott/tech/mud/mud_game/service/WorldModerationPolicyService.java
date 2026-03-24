package com.scott.tech.mud.mud_game.service;

import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.ModerationPreferences;
import com.scott.tech.mud.mud_game.persistence.entity.WorldSettingEntity;
import com.scott.tech.mud.mud_game.persistence.repository.WorldSettingRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the active moderation policy for the whole world.
 */
@Service
@Transactional
public class WorldModerationPolicyService {

    private static final String MODERATION_FILTERS_KEY = "moderation_filters";

    private final WorldSettingRepository worldSettingRepository;
    private volatile ModerationPreferences cachedPolicy;

    public WorldModerationPolicyService(WorldSettingRepository worldSettingRepository) {
        this.worldSettingRepository = worldSettingRepository;
    }

    @PostConstruct
    void init() {
        cachedPolicy = loadPolicy();
    }

    @Transactional(readOnly = true)
    public boolean blocks(ModerationCategory category) {
        return currentPolicy().blocks(category);
    }

    @Transactional(readOnly = true)
    public ModerationPreferences currentPolicy() {
        ModerationPreferences policy = cachedPolicy;
        if (policy == null) {
            policy = loadPolicy();
            cachedPolicy = policy;
        }
        return policy.copy();
    }

    public ModerationPreferences allow(ModerationCategory category) {
        return update(policy -> policy.allow(category));
    }

    public ModerationPreferences block(ModerationCategory category) {
        return update(policy -> policy.block(category));
    }

    public ModerationPreferences allowAll() {
        return update(ModerationPreferences::allowAll);
    }

    public ModerationPreferences blockAll() {
        return update(ModerationPreferences::blockAll);
    }

    private synchronized ModerationPreferences update(java.util.function.Consumer<ModerationPreferences> mutator) {
        ModerationPreferences policy = loadPolicy();
        mutator.accept(policy);
        worldSettingRepository.save(new WorldSettingEntity(MODERATION_FILTERS_KEY, policy.serialize()));
        cachedPolicy = policy.copy();
        return policy.copy();
    }

    private ModerationPreferences loadPolicy() {
        return worldSettingRepository.findById(MODERATION_FILTERS_KEY)
                .map(WorldSettingEntity::getSettingValue)
                .map(ModerationPreferences::fromSerialized)
                .orElseGet(ModerationPreferences::defaults);
    }
}
