package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSettingsRegistryTest {

    @Test
    void loadsConfiguredTitleAndFaviconFromLiveResources() throws IOException {
        Path resourcesRoot = Files.createTempDirectory("global-settings-test");
        Files.createDirectories(resourcesRoot.resolve("world/ui"));
        Files.writeString(resourcesRoot.resolve("world/global-settings.json"), """
                {
                  "title": "Lantern MUD",
                  "faviconPath": "world/ui/custom-favicon.svg"
                }
                """);
        Files.writeString(resourcesRoot.resolve("world/ui/custom-favicon.svg"), "<svg></svg>");

        GlobalSettingsRegistry.GlobalSettings settings =
                GlobalSettingsRegistry.loadSettings(new ObjectMapper(), resourcesRoot);
        GlobalSettingsRegistry.FaviconAsset favicon =
                GlobalSettingsRegistry.loadFavicon(new ObjectMapper(), resourcesRoot);

        assertThat(settings.title()).isEqualTo("Lantern MUD");
        assertThat(settings.faviconPath()).isEqualTo("world/ui/custom-favicon.svg");
        assertThat(new String(favicon.bytes(), StandardCharsets.UTF_8)).isEqualTo("<svg></svg>");
        assertThat(favicon.mediaType()).isEqualTo(MediaType.parseMediaType("image/svg+xml"));
    }

    @Test
    void fallsBackToDefaultsWhenGlobalSettingsFileIsMissing() throws IOException {
        Path resourcesRoot = Files.createTempDirectory("global-settings-defaults");
        Files.createDirectories(resourcesRoot.resolve("world/ui"));
        Files.writeString(resourcesRoot.resolve("world/ui/favicon.svg"), "<svg>default</svg>");

        GlobalSettingsRegistry.GlobalSettings settings =
                GlobalSettingsRegistry.loadSettings(new ObjectMapper(), resourcesRoot);
        GlobalSettingsRegistry.FaviconAsset favicon =
                GlobalSettingsRegistry.loadFavicon(new ObjectMapper(), resourcesRoot);

        assertThat(settings.title()).isEqualTo("MudGameUi");
        assertThat(settings.faviconPath()).isEqualTo("world/ui/favicon.svg");
        assertThat(new String(favicon.bytes(), StandardCharsets.UTF_8)).isEqualTo("<svg>default</svg>");
    }
}
