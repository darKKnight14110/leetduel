package com.leetduel.judge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Context-load smoke test - assumes docker-compose.infra.yml's RabbitMQ is
// already running locally (same assumption every other service's
// *ApplicationTests makes), plus a reachable Docker daemon for
// DockerClientFactory's bean (DockerClientImpl.getInstance doesn't connect
// eagerly, so no daemon call actually happens just from context startup).
@SpringBootTest
class JudgeWorkerApplicationTests {

    @Test
    void contextLoads() {
    }
}
