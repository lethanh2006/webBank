package com.webbank;

import org.springframework.boot.SpringApplication;

public class TestWebbankApplication {

	public static void main(String[] args) {
		SpringApplication.from(WebbankApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
