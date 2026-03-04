package com.scott.tech.mud.mud_game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MudGameApplication {

	public static void main(String[] args) {
		SpringApplication.run(MudGameApplication.class, args);
	}

}
