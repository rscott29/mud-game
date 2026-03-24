package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
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
                  "faviconPath": "world/ui/custom-favicon.svg",
                  "death": {
                    "itemLossEnabled": false,
                    "corpsePersistenceMinutes": 12
                  }
                }
                """);
        Files.writeString(resourcesRoot.resolve("world/ui/custom-favicon.svg"), "<svg></svg>");

        GlobalSettingsRegistry.GlobalSettings settings =
                GlobalSettingsRegistry.loadSettings(new ObjectMapper(), resourcesRoot);
        GlobalSettingsRegistry.FaviconAsset favicon =
                GlobalSettingsRegistry.loadFavicon(new ObjectMapper(), resourcesRoot);

        assertThat(settings.title()).isEqualTo("Lantern MUD");
        assertThat(settings.faviconPath()).isEqualTo("world/ui/custom-favicon.svg");
        assertThat(settings.death().itemLossEnabled()).isFalse();
        assertThat(settings.death().corpsePersistenceMinutes()).isEqualTo(12);
        assertThat(new String(favicon.bytes(), StandardCharsets.UTF_8)).isEqualTo("<svg></svg>");
        assertThat(favicon.mediaType()).isEqualTo(MediaType.parseMediaType("image/svg+xml"));
    }

    @Test
    void fallsBackToPackagedDefaultsWhenLiveSourceFilesAreMissing() throws IOException {
        Path resourcesRoot = Files.createTempDirectory("global-settings-defaults");
        ObjectMapper objectMapper = new ObjectMapper();

        GlobalSettingsRegistry.GlobalSettings settings =
                GlobalSettingsRegistry.loadSettings(objectMapper, resourcesRoot);
        GlobalSettingsRegistry.FaviconAsset favicon =
                GlobalSettingsRegistry.loadFavicon(objectMapper, resourcesRoot);
        GlobalSettingsRegistry.GlobalSettingsFile packagedSettings;
        try (var inputStream = new ClassPathResource("world/global-settings.json").getInputStream()) {
            packagedSettings = objectMapper.readValue(inputStream, GlobalSettingsRegistry.GlobalSettingsFile.class);
        }
        byte[] expectedFaviconBytes;
        try (var inputStream = new ClassPathResource(packagedSettings.faviconPath()).getInputStream()) {
            expectedFaviconBytes = inputStream.readAllBytes();
        }

        assertThat(settings.title()).isEqualTo(packagedSettings.title());
        assertThat(settings.faviconPath()).isEqualTo(packagedSettings.faviconPath());
        assertThat(settings.death().itemLossEnabled()).isEqualTo(packagedSettings.death().itemLossEnabled());
        assertThat(settings.death().corpsePersistenceMinutes()).isEqualTo(packagedSettings.death().corpsePersistenceMinutes());
        assertThat(favicon.bytes()).isEqualTo(expectedFaviconBytes);
    }
}
