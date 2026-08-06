package com.devbraid;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// TODO: re-run Flyway migration on a clean Docker DB to resolve checksum mismatch,
// then remove the spring.flyway.enabled=false property below
@SpringBootTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=none"})
class DevbraidBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
