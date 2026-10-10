package com.learningcompanion;

import com.learningcompanion.support.PostgresTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class LearningCompanionApplicationTests {

    @Test
    void contextLoads() {
    }
}
