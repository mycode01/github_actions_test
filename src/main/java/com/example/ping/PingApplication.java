package com.example.ping;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class PingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@Value("${app.msg}")
	private String msg;

	@GetMapping("/ping")
	public String ping(){
		return msg;
	}
}
