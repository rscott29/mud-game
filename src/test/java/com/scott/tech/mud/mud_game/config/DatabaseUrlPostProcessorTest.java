package com.scott.tech.mud.mud_game.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseUrlPostProcessorTest {

    private final DatabaseUrlPostProcessor processor = new DatabaseUrlPostProcessor();

    @Test
    void missingDatabaseUrl_doesNotInjectDatasourceProperties() {
        MockEnvironment env = new MockEnvironment();

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url")).isNull();
        assertThat(env.getProperty("spring.datasource.username")).isNull();
        assertThat(env.getProperty("spring.datasource.password")).isNull();
    }

    @Test
    void postgresUrl_withCredentialsAndPort_isConvertedToSpringDatasourceProps() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgres://mud_user:pw123@db.example.com:5433/muddb");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:5433/muddb");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("mud_user");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("pw123");
    }

    @Test
    void postgresqlUrl_withoutExplicitPort_defaultsTo5432() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DATABASE_URL", "postgresql://mud_user:pw123@db.example.com/muddb");

        processor.postProcessEnvironment(env, null);

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://db.example.com:5432/muddb");
    }

    @Test
    void malformedDatabaseUrl_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("DATABASE_URL", "not a real uri");

        assertThatThrownBy(() -> processor.postProcessEnvironment(env, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse DATABASE_URL");
    }
}
