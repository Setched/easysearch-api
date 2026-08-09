package me.setched.easysearch.api;

import org.springframework.boot.SpringApplication;

/**
 * Alternate entry point used to run the application locally against a Testcontainers-managed Postgres
 * instance (e.g. via {@code mvn spring-boot:test-run}), instead of a real database.
 */
public class TestEasysearchApiApplication {

	/**
	 * Starts the application with {@link TestcontainersConfiguration} imported.
	 *
	 * @param args command-line arguments
	 */
	public static void main(String[] args) {
		SpringApplication.from(EasysearchApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
