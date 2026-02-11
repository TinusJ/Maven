package com.tinusj.maven.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.buildpack.platform.docker.configuration.DockerConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MavenDockerConfiguration.
 */
public class MavenDockerConfigurationTest {

    @Test
    public void testAsDockerConfigurationWithDefaultConfig() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
    }

    @Test
    public void testAsDockerConfigurationWithHost() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        config.setHost("tcp://localhost:2376");
        config.setTlsVerify(true);
        config.setCertPath("/path/to/certs");

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
        assertNotNull(dockerConfig.getHost());
    }

    @Test
    public void testAsDockerConfigurationWithContext() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        config.setContext("my-context");

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
    }

    @Test
    public void testAsDockerConfigurationWithHostAndContextThrows() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        config.setHost("tcp://localhost:2376");
        config.setContext("my-context");

        assertThrows(IllegalArgumentException.class, config::asDockerConfiguration);
    }

    @Test
    public void testAsDockerConfigurationWithBuilderUserAuth() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        auth.setUsername("user");
        auth.setPassword("pass");
        auth.setUrl("https://registry.example.com");
        auth.setEmail("user@example.com");
        config.setBuilderRegistry(auth);

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
        assertNotNull(dockerConfig.getBuilderRegistryAuthentication());
    }

    @Test
    public void testAsDockerConfigurationWithBuilderTokenAuth() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        auth.setToken("my-token");
        config.setBuilderRegistry(auth);

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
        assertNotNull(dockerConfig.getBuilderRegistryAuthentication());
    }

    @Test
    public void testAsDockerConfigurationWithPublishUserAuth() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        auth.setUsername("user");
        auth.setPassword("pass");
        config.setPublishRegistry(auth);

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
        assertNotNull(dockerConfig.getPublishRegistryAuthentication());
    }

    @Test
    public void testAsDockerConfigurationWithPublishTokenAuth() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        auth.setToken("publish-token");
        config.setPublishRegistry(auth);

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
        assertNotNull(dockerConfig.getPublishRegistryAuthentication());
    }

    @Test
    public void testAsDockerConfigurationWithBindHostToBuilder() {
        MavenDockerConfiguration config = new MavenDockerConfiguration();
        config.setBindHostToBuilder(true);

        DockerConfiguration dockerConfig = config.asDockerConfiguration();

        assertNotNull(dockerConfig);
    }

    @Test
    public void testRegistryAuthIsEmpty() {
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        assertTrue(auth.isEmpty());

        auth.setUsername("user");
        assertFalse(auth.isEmpty());
    }

    @Test
    public void testRegistryAuthHasTokenAuth() {
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        assertFalse(auth.hasTokenAuth());

        auth.setToken("token");
        assertTrue(auth.hasTokenAuth());
    }

    @Test
    public void testRegistryAuthHasUserAuth() {
        MavenDockerConfiguration.RegistryAuth auth = new MavenDockerConfiguration.RegistryAuth();
        assertFalse(auth.hasUserAuth());

        auth.setUsername("user");
        auth.setPassword("pass");
        assertTrue(auth.hasUserAuth());
    }
}
