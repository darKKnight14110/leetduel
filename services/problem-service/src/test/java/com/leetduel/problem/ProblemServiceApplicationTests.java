package com.leetduel.problem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Context-load smoke test - assumes docker-compose.infra.yml's Postgres is
// already running locally, same assumption auth-service/user-service make.
@SpringBootTest
class ProblemServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
