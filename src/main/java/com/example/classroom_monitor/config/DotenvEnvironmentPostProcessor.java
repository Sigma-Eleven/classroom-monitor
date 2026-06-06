package com.example.classroom_monitor.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, org.springframework.boot.SpringApplication application) {
		Path dotenv = Path.of(System.getProperty("user.dir", ".")).resolve(".env").normalize();
		if (!Files.exists(dotenv)) {
			return;
		}

		List<String> lines;
		try {
			lines = Files.readAllLines(dotenv, StandardCharsets.UTF_8);
		}
		catch (Exception ignored) {
			return;
		}

		Map<String, Object> map = new LinkedHashMap<>();
		for (String line : lines) {
			if (!StringUtils.hasText(line)) {
				continue;
			}
			String s = line.trim();
			if (!StringUtils.hasText(s) || s.startsWith("#")) {
				continue;
			}
			int idx = s.indexOf('=');
			if (idx <= 0) {
				continue;
			}
			String key = s.substring(0, idx).trim();
			String value = s.substring(idx + 1).trim();
			if (!StringUtils.hasText(key)) {
				continue;
			}
			value = stripQuotes(value);
			map.put(key, value);
		}

		alias(map);

		if (map.isEmpty()) {
			return;
		}
		environment.getPropertySources().addFirst(new MapPropertySource("dotenv", map));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	private static String stripQuotes(String value) {
		if (!StringUtils.hasText(value) || value.length() < 2) {
			return value;
		}
		char first = value.charAt(0);
		char last = value.charAt(value.length() - 1);
		if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static void alias(Map<String, Object> map) {
		if (!StringUtils.hasText(String.valueOf(map.getOrDefault("KIMI_API_KEY", "")))) {
			Object moonshot = map.get("MOONSHOT_API_KEY");
			if (moonshot != null && StringUtils.hasText(String.valueOf(moonshot))) {
				map.put("KIMI_API_KEY", String.valueOf(moonshot).trim());
			}
		}

		Object explicit = map.get("KIMI_COMPLETIONS_PATH");
		if (explicit == null || !StringUtils.hasText(String.valueOf(explicit))) {
			String baseUrl = String.valueOf(map.getOrDefault("KIMI_BASE_URL", "")).trim();
			if (baseUrl.endsWith("/")) {
				baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
			}
			if (baseUrl.endsWith("/v1")) {
				map.put("KIMI_COMPLETIONS_PATH", "/chat/completions");
			}
		}
	}
}
