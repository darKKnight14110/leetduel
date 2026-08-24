package com.leetduel.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

// @EnableAsync backs EmailService's @Async send methods - see EmailService
// for why running those off the request thread matters (it's what actually
// closes the account-enumeration timing gap in forgot-password/resend, not
// just a performance nicety).
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class AuthServiceApplication {

	public static void main(String[] args) {
		// Pin the JVM's default zone to UTC before anything (JDBC driver
		// included) reads it. Without this, the app inherits the host OS's
		// locale-derived zone id - on Windows that resolves to legacy aliases
		// (e.g. "Asia/Calcutta") that some Postgres tzdata builds reject
		// outright at connection startup. Storing/communicating in UTC
		// everywhere is also just correct for a distributed system: it's the
		// only zone every service instance agrees on regardless of host.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
