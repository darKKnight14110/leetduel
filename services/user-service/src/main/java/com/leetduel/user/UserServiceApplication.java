package com.leetduel.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class UserServiceApplication {

	public static void main(String[] args) {
		// See auth-service's AuthServiceApplication for why: pin UTC before
		// the JDBC driver reads the JVM's ambient (host-locale) zone.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(UserServiceApplication.class, args);
	}

}
