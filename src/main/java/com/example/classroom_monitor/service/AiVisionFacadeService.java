package com.example.classroom_monitor.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.example.classroom_monitor.config.AppProperties;
import com.example.classroom_monitor.exception.AppException;
import com.example.classroom_monitor.model.AiRecognitionResult;
import com.example.classroom_monitor.model.StudentBehavior;
import com.example.classroom_monitor.model.StudentState;
import com.example.classroom_monitor.model.UploadRecord;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class AiVisionFacadeService implements AiVisionService {

	private final ObjectMapper objectMapper;
	private final AppProperties properties;
	private final ExecutorService executorService;
	private final Environment environment;
	private final RestClient restClient;

	public AiVisionFacadeService(
			ObjectMapper objectMapper,
			AppProperties properties,
			ExecutorService aiExecutorService,
			Environment environment
	) {
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.executorService = aiExecutorService;
		this.environment = environment;
		this.restClient = RestClient.builder()
				.baseUrl(readString("spring.ai.deepseek.base-url", "https://api.deepseek.com"))
				.build();
	}

	@Override
	public AiRecognitionResult recognize(UploadRecord upload) {
		if (!properties.getAi().isEnabled()) {
			return mockRecognition(upload);
		}

		if (!deepSeekChatEnabled() || !StringUtils.hasText(readString("spring.ai.deepseek.api-key", ""))) {
			return mockRecognition(upload);
		}

		Duration timeout = properties.getAi().getRequestTimeout();
		try {
			return CompletableFuture.supplyAsync(() -> doRecognize(upload), executorService)
					.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
					.join();
		}
		catch (Exception ex) {
			Throwable root = unwrap(ex);
			if (root instanceof java.util.concurrent.TimeoutException) {
				throw new AppException("AI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "AI 调用超时，请稍后重试或调大超时配置");
			}
			throw new AppException("AI_ERROR", HttpStatus.BAD_GATEWAY, "AI 识别失败，请检查 DeepSeek 配置与网络");
		}
	}

	private AiRecognitionResult doRecognize(UploadRecord upload) {
		String schema = """
				输出严格 JSON（不要 markdown 代码块），结构如下：
				{
				  "totalStudents": 0,
				  "behaviors": {
				    "ATTENTIVE": 0,
				    "HEAD_DOWN": 0,
				    "SLEEPING": 0,
				    "PHONE": 0,
				    "DISTRACTED": 0,
				    "OTHER": 0
				  },
				  "students": [
				    { "studentNo": 1, "behavior": "ATTENTIVE", "confidence": 0.0 }
				  ]
				}
				说明：
				- 学生人数不确定时请尽量估计，并让 behaviors 各项之和等于 totalStudents
				- students 数组最多返回 60 条，studentNo 从 1 开始
				- behavior 必须从 ATTENTIVE/HEAD_DOWN/SLEEPING/PHONE/DISTRACTED/OTHER 中选择
				""";

		String instruction = """
				你是一名课堂学习行为分析助手。请分析图片中的多名学生，区分：
				抬头听课(ATTENTIVE)、低头(HEAD_DOWN)、睡觉(SLEEPING)、玩手机(PHONE)、走神(DISTRACTED)、其他(OTHER)。
				请统计各类人数，并按上面的 JSON 结构输出。
				""";

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", readString("spring.ai.deepseek.chat.model", "deepseek-chat"));
		body.put("temperature", readDouble("spring.ai.deepseek.chat.temperature", 0.0));

		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", "你只输出 JSON，不要解释。"));

		List<Map<String, Object>> content = new ArrayList<>();
		content.add(Map.of("type", "text", "text", instruction + "\n" + schema));
		String dataUrl = imageDataUrl(upload);
		if (StringUtils.hasText(dataUrl)) {
			content.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
		}
		messages.add(Map.of("role", "user", "content", content));
		body.put("messages", messages);

		String apiKey = readString("spring.ai.deepseek.api-key", "");
		try {
			String raw = restClient.post()
					.uri("/chat/completions")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
					.header(HttpHeaders.CONTENT_TYPE, "application/json")
					.body(body)
					.retrieve()
					.body(String.class);
			String text = extractAssistantText(raw);
			return parseRecognition(text);
		}
		catch (RestClientResponseException e) {
			throw new AppException("AI_HTTP_ERROR", HttpStatus.BAD_GATEWAY, "AI 服务调用失败：" + e.getStatusCode().value());
		}
	}

	private AiRecognitionResult parseRecognition(String raw) {
		String json = extractFirstJsonObject(raw);
		try {
			JsonNode node = objectMapper.readTree(json);
			int total = node.path("totalStudents").asInt(0);

			Map<StudentBehavior, Integer> behaviors = new EnumMap<>(StudentBehavior.class);
			JsonNode b = node.path("behaviors");
			for (StudentBehavior behavior : StudentBehavior.values()) {
				behaviors.put(behavior, b.path(behavior.name()).asInt(0));
			}

			List<StudentState> students = new ArrayList<>();
			JsonNode s = node.path("students");
			if (s.isArray()) {
				for (JsonNode item : s) {
					if (students.size() >= 60) {
						break;
					}
					int studentNo = item.path("studentNo").asInt(students.size() + 1);
					String behaviorStr = item.path("behavior").asText("OTHER");
					Double confidence = item.hasNonNull("confidence") ? item.get("confidence").asDouble() : null;
					students.add(new StudentState(studentNo, safeBehavior(behaviorStr), confidence));
				}
			}

			if (total <= 0) {
				total = behaviors.values().stream().mapToInt(Integer::intValue).sum();
			}

			if (total <= 0) {
				total = Math.max(1, students.size());
			}

			return new AiRecognitionResult(total, behaviors, students);
		}
		catch (Exception e) {
			throw new AppException("AI_PARSE_ERROR", HttpStatus.BAD_GATEWAY, "AI 返回内容无法解析为结构化结果");
		}
	}

	private boolean deepSeekChatEnabled() {
		return environment.getProperty("spring.ai.deepseek.chat.enabled", Boolean.class, false);
	}

	private String imageDataUrl(UploadRecord upload) {
		if (upload == null || !StringUtils.hasText(upload.absolutePath()) || !StringUtils.hasText(upload.contentType())) {
			return null;
		}
		try {
			byte[] bytes = Files.readAllBytes(Path.of(upload.absolutePath()));
			String base64 = Base64.getEncoder().encodeToString(bytes);
			return "data:" + upload.contentType() + ";base64," + base64;
		}
		catch (IOException e) {
			return null;
		}
	}

	private String extractAssistantText(String rawResponse) {
		if (!StringUtils.hasText(rawResponse)) {
			return "{}";
		}
		try {
			JsonNode root = objectMapper.readTree(rawResponse);
			JsonNode choice0 = root.path("choices").isArray() && root.path("choices").size() > 0 ? root.path("choices").get(0) : null;
			if (choice0 == null) {
				return rawResponse;
			}
			String content = choice0.path("message").path("content").asText(null);
			if (StringUtils.hasText(content)) {
				return content;
			}
			JsonNode delta = choice0.path("delta");
			String deltaContent = delta.path("content").asText(null);
			if (StringUtils.hasText(deltaContent)) {
				return deltaContent;
			}
			return rawResponse;
		}
		catch (Exception e) {
			return rawResponse;
		}
	}

	private String readString(String key, String defaultValue) {
		String v = environment.getProperty(key);
		return StringUtils.hasText(v) ? v : defaultValue;
	}

	private double readDouble(String key, double defaultValue) {
		Double v = environment.getProperty(key, Double.class);
		return v == null ? defaultValue : v;
	}

	private static String extractFirstJsonObject(String raw) {
		if (!StringUtils.hasText(raw)) {
			return "{}";
		}
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		if (start >= 0 && end > start) {
			return raw.substring(start, end + 1);
		}
		return raw.trim();
	}

	private static StudentBehavior safeBehavior(String name) {
		try {
			return StudentBehavior.valueOf(name);
		}
		catch (Exception e) {
			return StudentBehavior.OTHER;
		}
	}

	private static Throwable unwrap(Throwable ex) {
		Throwable cur = ex;
		while (cur.getCause() != null && cur != cur.getCause()) {
			cur = cur.getCause();
		}
		return cur;
	}

	private AiRecognitionResult mockRecognition(UploadRecord upload) {
		long seed = seedFromUpload(upload);
		Random rnd = new Random(seed);

		int total = 10 + rnd.nextInt(31);
		Map<StudentBehavior, Double> base = Map.of(
				StudentBehavior.ATTENTIVE, 0.42,
				StudentBehavior.HEAD_DOWN, 0.24,
				StudentBehavior.SLEEPING, 0.05,
				StudentBehavior.PHONE, 0.06,
				StudentBehavior.DISTRACTED, 0.18,
				StudentBehavior.OTHER, 0.05
		);

		Map<StudentBehavior, Double> w = new EnumMap<>(StudentBehavior.class);
		double sumW = 0.0;
		for (StudentBehavior b : StudentBehavior.values()) {
			double v = base.getOrDefault(b, 0.0) * (0.6 + rnd.nextDouble());
			w.put(b, v);
			sumW += v;
		}

		Map<StudentBehavior, Integer> behaviors = new EnumMap<>(StudentBehavior.class);
		int allocated = 0;
		for (StudentBehavior b : StudentBehavior.values()) {
			int c = (int) Math.floor((w.getOrDefault(b, 0.0) / sumW) * total);
			behaviors.put(b, Math.max(0, c));
			allocated += behaviors.get(b);
		}

		int remaining = total - allocated;
		List<StudentBehavior> order = new ArrayList<>(List.of(StudentBehavior.values()));
		Collections.shuffle(order, rnd);
		for (int i = 0; i < remaining; i++) {
			StudentBehavior b = order.get(i % order.size());
			behaviors.put(b, behaviors.getOrDefault(b, 0) + 1);
		}

		if (behaviors.getOrDefault(StudentBehavior.ATTENTIVE, 0) == 0) {
			StudentBehavior takeFrom = behaviors.getOrDefault(StudentBehavior.HEAD_DOWN, 0) > 0 ? StudentBehavior.HEAD_DOWN : StudentBehavior.OTHER;
			if (behaviors.getOrDefault(takeFrom, 0) > 0) {
				behaviors.put(takeFrom, behaviors.getOrDefault(takeFrom, 0) - 1);
				behaviors.put(StudentBehavior.ATTENTIVE, 1);
			}
		}

		List<StudentState> students = new ArrayList<>();
		List<StudentBehavior> expanded = new ArrayList<>();
		for (StudentBehavior b : StudentBehavior.values()) {
			int c = behaviors.getOrDefault(b, 0);
			for (int i = 0; i < c; i++) {
				expanded.add(b);
			}
		}
		Collections.shuffle(expanded, rnd);
		int no = 1;
		for (StudentBehavior b : expanded) {
			if (students.size() >= 60) {
				break;
			}
			double confidence = 0.6 + (rnd.nextDouble() * 0.35);
			students.add(new StudentState(no++, b, Math.round(confidence * 100.0) / 100.0));
		}
		return new AiRecognitionResult(total, behaviors, students);
	}

	private static long seedFromUpload(UploadRecord upload) {
		if (upload == null) {
			return 0L;
		}
		if (StringUtils.hasText(upload.absolutePath())) {
			try {
				byte[] bytes = Files.readAllBytes(Path.of(upload.absolutePath()));
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				byte[] d = md.digest(bytes);
				long v = 0L;
				for (int i = 0; i < Math.min(8, d.length); i++) {
					v = (v << 8) | (d[i] & 0xffL);
				}
				return v;
			}
			catch (Exception ignored) {
			}
		}
		String key = StringUtils.hasText(upload.uploadId()) ? upload.uploadId() : upload.originalFilename();
		return key == null ? 0L : key.hashCode();
	}
}
