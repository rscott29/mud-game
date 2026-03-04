package com.scott.tech.mud.mud_game.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Converts Railway's DATABASE_URL (postgresql://user:pass@host:port/db)
 * into Spring datasource properties at startup, before the context is built.
 */
public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }

        try {
            URI uri = URI.create(databaseUrl.replace("postgres://", "postgresql://"));
            String userInfo = uri.getUserInfo();
            String host     = uri.getHost();
            int    port     = uri.getPort() == -1 ? 5432 : uri.getPort();
            String db       = uri.getPath().replaceFirst("^/", "");

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url",
                    "jdbc:postgresql://" + host + ":" + port + "/" + db);

            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                props.put("spring.datasource.username", parts[0]);
                props.put("spring.datasource.password", parts[1]);
            }

            environment.getPropertySources()
                    .addFirst(new MapPropertySource("railwayDatabaseUrl", props));

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse DATABASE_URL: " + databaseUrl, e);
        }
    }
}
