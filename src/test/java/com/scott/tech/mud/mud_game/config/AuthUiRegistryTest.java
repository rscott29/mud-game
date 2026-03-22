package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthUiRegistryTest {

    @Test
    void loadsBannerFromConfiguredAsset() {
        AuthUiRegistry registry = new AuthUiRegistry(new ObjectMapper());

        assertThat(registry.banner()).contains("/' .,,,,  ./");
        assertThat(registry.banner()).doesNotContain("THE OBSIDIAN KINGDOM");
        assertThat(registry.banner()).doesNotEndWith("\n");
    }

    @Test
    void prefersLiveSourceResourcesWhenAvailable() throws IOException {
        Path resourcesRoot = Files.createTempDirectory("auth-ui-test");
        Files.createDirectories(resourcesRoot.resolve("world/ui"));
        Files.writeString(resourcesRoot.resolve("world/auth-ui.json"), """
                {
                  "bannerPath": "world/ui/auth-banner.txt"
                }
                """);
        Files.writeString(resourcesRoot.resolve("world/ui/auth-banner.txt"), "LIVE BANNER\n");

        assertThat(AuthUiRegistry.loadBanner(new ObjectMapper(), resourcesRoot)).isEqualTo("LIVE BANNER");
    }
}
