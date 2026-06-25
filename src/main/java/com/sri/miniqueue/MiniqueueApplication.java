package com.sri.miniqueue;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Miniqueue API", version = "v1"))
public class MiniqueueApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniqueueApplication.class, args);
	}

}