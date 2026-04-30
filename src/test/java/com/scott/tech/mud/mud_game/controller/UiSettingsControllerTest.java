package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry;
import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry.DeathSettings;
import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry.FaviconAsset;
import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry.GlobalSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiSettingsController.class)
class UiSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GlobalSettingsRegistry globalSettingsRegistry;

    @Test
    void getUiSettings_returnsTitleAndFaviconUrl() throws Exception {
        when(globalSettingsRegistry.settings()).thenReturn(
                new GlobalSettings("My MUD", "world/ui/favicon.svg", new DeathSettings(true, 30))
        );
        when(globalSettingsRegistry.favicon()).thenReturn(
                new FaviconAsset(new byte[] {1, 2, 3}, MediaType.valueOf("image/svg+xml"))
        );

        mockMvc.perform(get("/api/ui/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("My MUD"))
                .andExpect(jsonPath("$.faviconUrl").value("/api/ui/favicon/favicon.svg"))
                .andExpect(jsonPath("$.faviconType").value("image/svg+xml"));
    }

    @Test
    void getFavicon_returnsBytesWithCorrectContentType() throws Exception {
        byte[] payload = new byte[] {10, 20, 30};
        when(globalSettingsRegistry.favicon()).thenReturn(
                new FaviconAsset(payload, MediaType.valueOf("image/png"))
        );

        mockMvc.perform(get("/api/ui/favicon"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentType(MediaType.valueOf("image/png")))
                .andExpect(content().bytes(payload));
    }
}
