package com.vyoop.fieldtrack;

import org.springframework.boot.SpringApplication;

public class TestFieldTrackApplication {

	public static void main(String[] args) {
		SpringApplication.from(FieldTrackApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
