package com.sos;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SosApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that Spring context + all JPA entities load without errors
    }
}
