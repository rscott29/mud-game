package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ui")
public class UiSettingsController {

    private final GlobalSettingsRegistry globalSettingsRegistry;

    public UiSettingsController(GlobalSettingsRegistry globalSettingsRegistry) {
        this.globalSettingsRegistry = globalSettingsRegistry;
    }

    @GetMapping("/settings")
    public ResponseEntity<UiSettingsResponse> getUiSettings() {
        var settings = globalSettingsRegistry.settings();
        var favicon = globalSettingsRegistry.favicon();
        return ResponseEntity.ok(new UiSettingsResponse(
                settings.title(),
                "/api/ui/favicon/" + fileName(settings.faviconPath()),
                favicon.mediaType().toString()
        ));
    }

    @GetMapping({"/favicon", "/favicon/{name:.+}"})
    public ResponseEntity<byte[]> getFavicon(@PathVariable(name = "name", required = false) String ignoredName) {
        var favicon = globalSettingsRegistry.favicon();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(favicon.mediaType())
                .body(favicon.bytes());
    }

    private static String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "favicon";
        }

        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            return path.substring(slash + 1);
        }

        return path;
    }

    public record UiSettingsResponse(String title, String faviconUrl, String faviconType) {}
}
