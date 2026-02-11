package com.tinusj.maven.config;

import org.springframework.boot.buildpack.platform.docker.configuration.DockerConfiguration;
import org.springframework.boot.buildpack.platform.docker.configuration.DockerRegistryAuthentication;

/**
 * Maven Docker configuration for the build-image goal.
 * Provides settings for connecting to Docker daemon and authenticating with registries.
 */
public class MavenDockerConfiguration {

    /**
     * Docker daemon host URL (e.g., "tcp://192.168.99.100:2376").
     * Mutually exclusive with context.
     */
    private String host;

    /**
     * Docker context name to use.
     * Mutually exclusive with host.
     */
    private String context;

    /**
     * Whether to verify TLS certificates when connecting to Docker daemon.
     */
    private boolean tlsVerify;

    /**
     * Path to directory containing TLS certificates (ca.pem, cert.pem, key.pem).
     */
    private String certPath;

    /**
     * Whether to bind the host information to the builder container.
     */
    private boolean bindHostToBuilder;

    /**
     * Authentication for the builder registry.
     */
    private RegistryAuth builderRegistry;

    /**
     * Authentication for the publish registry.
     */
    private RegistryAuth publishRegistry;

    /**
     * Registry authentication configuration.
     */
    public static class RegistryAuth {
        private String username;
        private String password;
        private String url;
        private String email;
        private String token;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public boolean isEmpty() {
            return username == null && password == null && token == null;
        }

        public boolean hasTokenAuth() {
            return token != null && !token.isEmpty();
        }

        public boolean hasUserAuth() {
            return username != null && !username.isEmpty() && password != null && !password.isEmpty();
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public boolean isTlsVerify() {
        return tlsVerify;
    }

    public void setTlsVerify(boolean tlsVerify) {
        this.tlsVerify = tlsVerify;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public boolean isBindHostToBuilder() {
        return bindHostToBuilder;
    }

    public void setBindHostToBuilder(boolean bindHostToBuilder) {
        this.bindHostToBuilder = bindHostToBuilder;
    }

    public RegistryAuth getBuilderRegistry() {
        return builderRegistry;
    }

    public void setBuilderRegistry(RegistryAuth builderRegistry) {
        this.builderRegistry = builderRegistry;
    }

    public RegistryAuth getPublishRegistry() {
        return publishRegistry;
    }

    public void setPublishRegistry(RegistryAuth publishRegistry) {
        this.publishRegistry = publishRegistry;
    }

    /**
     * Converts this configuration to a DockerConfiguration for use with the buildpack platform.
     *
     * @return DockerConfiguration instance
     * @throws IllegalArgumentException if both host and context are specified
     */
    public DockerConfiguration asDockerConfiguration() {
        if (host != null && !host.isEmpty() && context != null && !context.isEmpty()) {
            throw new IllegalArgumentException("Only one of 'host' or 'context' can be specified");
        }

        DockerConfiguration config = new DockerConfiguration();

        // Configure Docker host
        if (host != null && !host.isEmpty()) {
            config = config.withHost(host, tlsVerify, certPath);
        } else if (context != null && !context.isEmpty()) {
            config = config.withContext(context);
        }

        // Configure builder registry authentication
        if (builderRegistry != null && !builderRegistry.isEmpty()) {
            if (builderRegistry.hasTokenAuth()) {
                config = config.withBuilderRegistryTokenAuthentication(builderRegistry.getToken());
            } else if (builderRegistry.hasUserAuth()) {
                config = config.withBuilderRegistryUserAuthentication(
                        builderRegistry.getUsername(),
                        builderRegistry.getPassword(),
                        builderRegistry.getUrl(),
                        builderRegistry.getEmail()
                );
            }
        }

        // Configure publish registry authentication
        if (publishRegistry != null && !publishRegistry.isEmpty()) {
            if (publishRegistry.hasTokenAuth()) {
                config = config.withPublishRegistryTokenAuthentication(publishRegistry.getToken());
            } else if (publishRegistry.hasUserAuth()) {
                config = config.withPublishRegistryUserAuthentication(
                        publishRegistry.getUsername(),
                        publishRegistry.getPassword(),
                        publishRegistry.getUrl(),
                        publishRegistry.getEmail()
                );
            }
        }

        // Configure bind host to builder
        if (bindHostToBuilder) {
            config = config.withBindHostToBuilder(true);
        }

        return config;
    }
}
