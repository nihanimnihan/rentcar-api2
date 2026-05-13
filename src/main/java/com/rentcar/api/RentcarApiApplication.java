package com.rentcar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RentcarApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(RentcarApiApplication.class, args);
	}

}
