package me.setched.easysearch.api;

import org.springframework.boot.SpringApplication;

public class TestEasysearchApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(EasysearchApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
