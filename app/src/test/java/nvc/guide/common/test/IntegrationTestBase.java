package nvc.guide.common.test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类
 * 使用 Testcontainers 提供 PostgreSQL + Redis 容器，无需本地安装
 *
 * 使用方式：
 *   class MyServiceIntegrationTest extends IntegrationTestBase { ... }
 */
@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers
public abstract class IntegrationTestBase {

    @Container
    protected static final PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("nvc_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    protected static final GenericContainer<?> redis =
        new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis (Redisson)
        registry.add("spring.redis.redisson.config",
            () -> String.format("""
                singleServerConfig:
                  address: "redis://%s:%d"
                  database: 0
                """, redis.getHost(), redis.getMappedPort(6379)));
    }
}
