package uz.backenddoctor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Smoke test: the Spring context must start against a real Postgres
 * (via Testcontainers) with the Flyway migration applied.
 */
@SpringBootTest
@Testcontainers
class BackendDoctorApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("backend_doctor")
            .withUsername("backend_doctor")
            .withPassword("backend_doctor");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Skip seeding a huge dataset just to verify the context loads.
        registry.add("seed.customers", () -> 10);
        registry.add("seed.products", () -> 10);
        registry.add("seed.orders", () -> 10);
    }

    @Test
    void contextLoads() {
    }
}
