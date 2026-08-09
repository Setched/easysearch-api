package me.setched.easysearch.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Smoke test verifying the full Spring application context wires up without errors.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EasysearchApiApplicationTests {

	/**
	 * Verifies that the application context loads successfully, with a real Postgres container backing it.
	 */
	@Test
	void contextLoads() {
	}

}
