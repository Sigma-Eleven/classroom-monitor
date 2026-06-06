package com.example.classroom_monitor.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
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

	private static final Logger log = LoggerFactory.getLogger(AiVisionFacadeService.class);

	private final ObjectProvider<ChatModel> chatModelProvider;
	private final ObjectMapper objectMapper;
	private final AppProperties properties;
	private final ExecutorService executorService;

	public AiVisionFacadeService(
			ObjectProvider<ChatModel> chatModelProvider,
			ObjectMapper objectMapper,
			AppProperties properties,
			ExecutorService aiExecutorService
	) {
		this.chatModelProvider = chatModelProvider;
		this.objectMapper = objectMapper;
		this.properties = properties;
		this.executorService = aiExecutorService;
	}

	@Override
	public AiRecognitionResult recognize(UploadRecord upload) {
		if (!properties.getAi().isEnabled()) {
			return mockRecognition(upload);
		}

		ChatModel chatModel = chatModelProvider.getIfAvailable();
		if (chatModel == null) {
			return mockRecognition(upload);
		}

		Duration timeout = properties.getAi().getRequestTimeout();
		try {
			return CompletableFuture.supplyAsync(() -> doRecognize(chatModel, upload), executorService)
					.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
					.join();
		}
		catch (Exception ex) {
			Throwable root = unwrap(ex);
			if (root instanceof java.util.concurrent.TimeoutException) {
				throw new AppException("AI_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT, "AI 调用超时，请稍后重试或调大超时配置");
			}
			logAiFailure(upload, root);
			throw new AppException("AI_ERROR", HttpStatus.BAD_GATEWAY, buildAiErrorMessage(root));
		}
	}

	private void logAiFailure(UploadRecord upload, Throwable root) {
		if (root instanceof RestClientResponseException e) {
			String body = e.getResponseBodyAsString();
			if (body != null && body.length() > 2000) {
				body = body.substring(0, 2000);
			}
			log.warn("AI request failed. uploadId={}, status={}, error={}, body={}", upload.uploadId(), e.getStatusCode().value(), safeOneLine(e.getMessage()), safeOneLine(body));
			return;
		}
		log.warn("AI request failed. uploadId={}, error={}", upload.uploadId(), safeOneLine(root.toString()));
	}

	private String buildAiErrorMessage(Throwable root) {
		if (root instanceof RestClientResponseException e) {
			int status = e.getStatusCode().value();
			String providerHint = "AI 识别失败，请检查 Kimi 配置与网络";
			if (status == 401 || status == 403) {
				return providerHint + "（鉴权失败：请检查 KIMI_API_KEY / 权限）";
			}
			if (status == 429) {
				return providerHint + "（触发限流：请稍后重试或检查额度）";
			}
			if (status >= 500) {
				return providerHint + "（服务端错误：" + status + "）";
			}
			return providerHint + "（HTTP " + status + "）";
		}
		String msg = String.valueOf(root);
		if (msg.contains("/v1/v1/") || msg.contains("url.not_found")) {
			return "AI 识别失败：KIMI_BASE_URL 配置不正确（不要带 /v1），例如 https://api.moonshot.cn";
		}
		if (msg.contains("Not found the model") || msg.contains("Permission denied") || msg.contains("model")) {
			return "AI 识别失败：模型不可用或无权限，请检查 KIMI_MODEL 是否为你账号可用的视觉模型";
		}
		return "AI 识别失败，请检查 Kimi 配置与网络";
	}

	private static String safeOneLine(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("\r", " ").replace("\n", " ").trim();
	}

	private AiRecognitionResult doRecognize(ChatModel chatModel, UploadRecord upload) {
		MimeType mimeType = toMimeType(upload.contentType());
		var imageResource = new FileSystemResource(upload.absolutePath());

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

		List<Message> messages = List.of(
				new SystemMessage("你只输出 JSON，不要解释。"),
				UserMessage.builder()
						.text(instruction + "\n" + schema)
						.media(List.of(new Media(mimeType, imageResource)))
						.build()
		);

		ChatResponse response = chatModel.call(new Prompt(messages));
		String text = Objects.requireNonNull(response.getResult().getOutput().getText());
		return parseRecognition(text);
	}

	private AiRecognitionResult parseRecognition(String raw) {
		log.debug("AI raw response: {}", raw);
		String json = extractFirstJsonObject(raw);
		try {
			return doParse(json);
		}
		catch (Exception e) {
			log.warn("Initial parse failed, attempting repair. error={}", e.getMessage());
			try {
				String repaired = repairJson(json);
				log.debug("Repaired JSON: {}", repaired);
				return doParse(repaired);
			}
			catch (Exception e2) {
				log.warn("Failed to parse AI response even after repair. raw={}, extracted_json={}", raw, json, e2);
				throw new AppException("AI_PARSE_ERROR", HttpStatus.BAD_GATEWAY, "AI 返回内容无法解析为结构化结果");
			}
		}
	}

	private AiRecognitionResult doParse(String json) throws Exception {
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
				// 如果是截断的对象，跳过不完整的项
				if (!item.isObject() || !item.has("behavior")) {
					continue;
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

	/**
	 * 简单的 JSON 修复逻辑，用于处理因 Token 限制被截断的情况
	 */
	private static String repairJson(String json) {
		if (!StringUtils.hasText(json)) {
			return "{}";
		}
		
		// 1. 找到最后一个完整的对象/数组结束符
		int lastBrace = json.lastIndexOf('}');
		int lastBracket = json.lastIndexOf(']');
		int lastValidEnd = Math.max(lastBrace, lastBracket);
		
		if (lastValidEnd <= 0) return json;
		
		// 2. 截断到最后一个可能完整的位置
		String truncated = json.substring(0, lastValidEnd + 1);
		
		// 3. 补全缺失的闭合符号
		StringBuilder repaired = new StringBuilder(truncated);
		int openBraces = countOccurrences(truncated, '{') - countOccurrences(truncated, '}');
		int openBrackets = countOccurrences(truncated, '[') - countOccurrences(truncated, ']');
		
		while (openBrackets > 0) {
			repaired.append(']');
			openBrackets--;
		}
		while (openBraces > 0) {
			repaired.append('}');
			openBraces--;
		}
		
		return repaired.toString();
	}

	private static int countOccurrences(String str, char c) {
		int count = 0;
		for (int i = 0; i < str.length(); i++) {
			if (str.charAt(i) == c) count++;
		}
		return count;
	}

	private static String extractFirstJsonObject(String raw) {
		if (!StringUtils.hasText(raw)) {
			return "{}";
		}

		// 1. 优先尝试提取 Markdown 代码块中的内容
		int codeStart = raw.indexOf("```json");
		if (codeStart >= 0) {
			int contentStart = codeStart + 7;
			int codeEnd = raw.indexOf("```", contentStart);
			if (codeEnd > contentStart) {
				String content = raw.substring(contentStart, codeEnd).trim();
				if (content.startsWith("{") && content.endsWith("}")) {
					return content;
				}
			}
		}

		// 2. 寻找第一个 { 和最后一个 }
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

	private static MimeType toMimeType(String contentType) {
		if (!StringUtils.hasText(contentType)) {
			return MimeTypeUtils.IMAGE_JPEG;
		}
		return MimeType.valueOf(contentType);
	}

	private static Throwable unwrap(Throwable ex) {
		Throwable cur = ex;
		while (cur.getCause() != null && cur != cur.getCause()) {
			cur = cur.getCause();
		}
		return cur;
	}

	private AiRecognitionResult mockRecognition(UploadRecord upload) {
		int total = 30;

		Map<StudentBehavior, Integer> behaviors = new EnumMap<>(StudentBehavior.class);
		behaviors.put(StudentBehavior.ATTENTIVE, 16);
		behaviors.put(StudentBehavior.HEAD_DOWN, 7);
		behaviors.put(StudentBehavior.DISTRACTED, 3);
		behaviors.put(StudentBehavior.PHONE, 2);
		behaviors.put(StudentBehavior.SLEEPING, 1);
		behaviors.put(StudentBehavior.OTHER, 1);

		List<StudentState> students = new ArrayList<>();
		int no = 1;
		for (StudentBehavior b : List.of(
				StudentBehavior.ATTENTIVE,
				StudentBehavior.HEAD_DOWN,
				StudentBehavior.DISTRACTED,
				StudentBehavior.PHONE,
				StudentBehavior.SLEEPING,
				StudentBehavior.OTHER
		)) {
			int c = behaviors.getOrDefault(b, 0);
			for (int i = 0; i < c && students.size() < 60; i++) {
				students.add(new StudentState(no++, b, 0.85));
			}
		}

		return new AiRecognitionResult(total, behaviors, students);
	}
}
