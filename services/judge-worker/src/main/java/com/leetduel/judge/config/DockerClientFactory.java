package com.leetduel.judge.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Named *Factory, not *Config, specifically to avoid colliding with
// docker-java's own com.github.dockerjava.core.DockerClientConfig type
// imported below - two types named DockerClientConfig in one file isn't
// otherwise resolvable without fully-qualifying one of them everywhere.
//
// One long-lived DockerClient/connection for the whole app - not
// re-created per submission. DefaultDockerClientConfig.createDefaultConfigBuilder()
// resolves DOCKER_HOST the same way the `docker` CLI does (env var, or the
// platform default socket) - inside this containerized service that
// resolves to the mounted /var/run/docker.sock, no explicit host needed.
@Configuration
public class DockerClientFactory {

    @Bean
    public DockerClient dockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}
