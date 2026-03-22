package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

/**
 * Loads configurable auth/login presentation content from world data so the
 * login experience can be customized without editing Java code.
 */
@Component
public class AuthUiRegistry {

    private static final Logger log = LoggerFactory.getLogger(AuthUiRegistry.class);
    private static final String CONFIG_PATH = "world/auth-ui.json";
    private static final Path DEFAULT_RESOURCES_ROOT = Path.of("src", "main", "resources")
            .toAbsolutePath()
            .normalize();

    private final ObjectMapper objectMapper;

    public AuthUiRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String banner() {
        return loadBanner(objectMapper, DEFAULT_RESOURCES_ROOT);
    }

    static String loadBanner(ObjectMapper objectMapper, Path resourcesRoot) {
        try (InputStream is = openResource(resourcesRoot, CONFIG_PATH)) {
            AuthUiFile file = objectMapper.readValue(is, AuthUiFile.class);
            validate(file);
            String loadedBanner = resolveBanner(file, resourcesRoot).stripTrailing();
            log.debug("Loaded auth banner from {}", CONFIG_PATH);
            return loadedBanner;
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to load {}: {}. Falling back to auth.banner message.", CONFIG_PATH, e.getMessage());
            return Messages.get("auth.banner").stripTrailing();
        }
    }

    private static void validate(AuthUiFile file) {
        if (file == null) {
            throw new IllegalStateException("Auth UI file is null");
        }
        boolean hasInlineBanner = file.banner() != null && !file.banner().isBlank();
        boolean hasBannerPath = file.bannerPath() != null && !file.bannerPath().isBlank();
        if (!hasInlineBanner && !hasBannerPath) {
            throw new IllegalStateException("Auth UI config must define banner or bannerPath");
        }
    }

    private static String resolveBanner(AuthUiFile file, Path resourcesRoot) {
        if (file.banner() != null && !file.banner().isBlank()) {
            return file.banner();
        }
        return loadTextAsset(resourcesRoot, file.bannerPath());
    }

    private static String loadTextAsset(Path resourcesRoot, String classpathPath) {
        try (InputStream is = openResource(resourcesRoot, classpathPath)) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load auth banner asset '" + classpathPath + "'", e);
        }
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
            throw new IllegalStateException("Auth UI resource path escapes resources root: " + resourcePath);
        }
        return resolvedPath;
    }

    public record AuthUiFile(String banner, String bannerPath) {}
}
