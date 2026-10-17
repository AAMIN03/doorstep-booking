package com.booking;

import org.springframework.boot.SpringApplication;

public class TestDoorstepBookingApplication {

	public static void main(String[] args) {
		SpringApplication.from(DoorstepBookingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
