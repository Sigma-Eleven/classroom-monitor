package com.example.classroom_monitor.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

	private Upload upload = new Upload();

	private Ai ai = new Ai();

	private Analysis analysis = new Analysis();

	@Data
	public static class Upload {
		private String dir = "./uploads";
		private DataSize maxSize = DataSize.ofMegabytes(10);
		private List<String> allowedContentTypes = List.of("image/jpeg", "image/png", "image/webp");
		private List<String> allowedExtensions = List.of("jpg", "jpeg", "png", "webp");
	}

	@Data
	public static class Ai {
		private boolean enabled = false;
		private Duration requestTimeout = Duration.ofSeconds(30);
	}

	@Data
	public static class Analysis {
		private Map<String, Double> weights = Map.of(
				"ATTENTIVE", 1.0,
				"HEAD_DOWN", 0.6,
				"SLEEPING", 0.0,
				"PHONE", 0.1,
				"DISTRACTED", 0.4,
				"OTHER", 0.5
		);
	}
}
