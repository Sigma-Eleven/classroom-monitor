package com.example.classroom_monitor.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfig {

	@Bean(destroyMethod = "shutdown")
	public ExecutorService aiExecutorService() {
		return Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
	}
}
