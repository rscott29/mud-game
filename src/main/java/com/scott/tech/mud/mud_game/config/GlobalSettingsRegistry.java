package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads global UI settings that should be easy to customize per world build.
 */
@Component
public class GlobalSettingsRegistry {

    private static final Logger log = LoggerFactory.getLogger(GlobalSettingsRegistry.class);
    private static final String CONFIG_PATH = "world/global-settings.json";
    private static final String DEFAULT_TITLE = "MudGameUi";
    private static final String DEFAULT_FAVICON_PATH = "world/ui/favicon.svg";
    private static final boolean DEFAULT_DEATH_ITEM_LOSS_ENABLED = true;
    private static final int DEFAULT_CORPSE_PERSISTENCE_MINUTES = 30;
    private static final Path DEFAULT_RESOURCES_ROOT = Path.of("src", "main", "resources")
            .toAbsolutePath()
            .normalize();

    private final ObjectMapper objectMapper;

    public GlobalSettingsRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GlobalSettings settings() {
        return loadSettings(objectMapper, DEFAULT_RESOURCES_ROOT);
    }

    public FaviconAsset favicon() {
        return loadFavicon(objectMapper, DEFAULT_RESOURCES_ROOT);
    }

    static GlobalSettings loadSettings(ObjectMapper objectMapper, Path resourcesRoot) {
        try (InputStream is = openResource(resourcesRoot, CONFIG_PATH)) {
            GlobalSettingsFile file = objectMapper.readValue(is, GlobalSettingsFile.class);
            GlobalSettings settings = normalize(file);
            log.debug("Loaded global settings from {}", CONFIG_PATH);
            return settings;
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to load {}: {}. Falling back to defaults.", CONFIG_PATH, e.getMessage());
            return defaults();
        }
    }

    static FaviconAsset loadFavicon(ObjectMapper objectMapper, Path resourcesRoot) {
        GlobalSettings settings = loadSettings(objectMapper, resourcesRoot);
        try (InputStream is = openResource(resourcesRoot, settings.faviconPath())) {
            return new FaviconAsset(
                    is.readAllBytes(),
                    MediaTypeFactory.getMediaType(settings.faviconPath()).orElse(MediaType.APPLICATION_OCTET_STREAM)
            );
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to load favicon '{}': {}. Falling back to default favicon.", settings.faviconPath(), e.getMessage());
            try (InputStream is = openResource(resourcesRoot, DEFAULT_FAVICON_PATH)) {
                return new FaviconAsset(
                        is.readAllBytes(),
                        MediaTypeFactory.getMediaType(DEFAULT_FAVICON_PATH).orElse(MediaType.APPLICATION_OCTET_STREAM)
                );
            } catch (IOException fallbackError) {
                throw new IllegalStateException("Failed to load default favicon asset", fallbackError);
            }
        }
    }

    private static GlobalSettings normalize(GlobalSettingsFile file) {
        if (file == null) {
            return defaults();
        }

        String title = hasText(file.title()) ? file.title().trim() : DEFAULT_TITLE;
        String faviconPath = hasText(file.faviconPath()) ? file.faviconPath().trim() : DEFAULT_FAVICON_PATH;
        DeathSettings deathSettings = normalizeDeath(file.death());
        return new GlobalSettings(title, faviconPath, deathSettings);
    }

    private static DeathSettings normalizeDeath(DeathSettingsFile file) {
        if (file == null) {
            return deathDefaults();
        }

        boolean itemLossEnabled = file.itemLossEnabled() == null
                ? DEFAULT_DEATH_ITEM_LOSS_ENABLED
                : file.itemLossEnabled();
        int corpsePersistenceMinutes = file.corpsePersistenceMinutes() == null
                ? DEFAULT_CORPSE_PERSISTENCE_MINUTES
                : Math.max(1, file.corpsePersistenceMinutes());
        return new DeathSettings(itemLossEnabled, corpsePersistenceMinutes);
    }

    private static GlobalSettings defaults() {
        return new GlobalSettings(DEFAULT_TITLE, DEFAULT_FAVICON_PATH, deathDefaults());
    }

    private static DeathSettings deathDefaults() {
        return new DeathSettings(DEFAULT_DEATH_ITEM_LOSS_ENABLED, DEFAULT_CORPSE_PERSISTENCE_MINUTES);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static InputStream openResource(Path resourcesRoot, String resourcePath) throws IOException {
        Path sourcePath = resolveSourcePath(resourcesRoot, resourcePath);
        if (Files.isRegularFile(sourcePath)) {
            return Files.newInputStream(sourcePath);
        }
        return new ClassPathResource(resourcePath).getInputStream();
    }

    private static Path resolveSourcePath(Path resourcesRoot, String resourcePath) {
        Path normalizedRoot = resourcesRoot.toAbsolutePath().normalize();
        Path resolvedPath = normalizedRoot.resolve(Path.of(resourcePath)).normalize();
        if (!resolvedPath.startsWith(normalizedRoot)) {
            throw new IllegalStateException("Global settings resource path escapes resources root: " + resourcePath);
        }
        return resolvedPath;
    }

    public record GlobalSettings(String title, String faviconPath, DeathSettings death) {}

    public record DeathSettings(boolean itemLossEnabled, int corpsePersistenceMinutes) {}

    public record FaviconAsset(byte[] bytes, MediaType mediaType) {}

    public record GlobalSettingsFile(String title, String faviconPath, DeathSettingsFile death) {}

    public record DeathSettingsFile(Boolean itemLossEnabled, Integer corpsePersistenceMinutes) {}
}
