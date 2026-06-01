package com.example.classroom_monitor;

import com.example.classroom_monitor.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class ClassroomMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClassroomMonitorApplication.class, args);
	}

}
