package com.rentcar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

@SpringBootApplication
@ConfigurationPropertiesScan
@org.springframework.scheduling.annotation.EnableScheduling
public class RentcarApiApplication {

	public static void main(String[] args) {
		// Pin the JVM to UTC so LocalDateTime.now() is always deterministic regardless of
		// server OS timezone. Pickup/dropoff comparisons use BusinessTimezone.nowBusinessLocal() instead.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(RentcarApiApplication.class, args);
	}

}
