package com.example.classroom_monitor.service;

import com.example.classroom_monitor.config.AppProperties;
import com.example.classroom_monitor.exception.AppException;
import com.example.classroom_monitor.model.AiRecognitionResult;
import com.example.classroom_monitor.model.StudentBehavior;
import com.example.classroom_monitor.model.StudentState;
import com.example.classroom_monitor.model.UploadRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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

@Service
public class AiVisionFacadeService implements AiVisionService {

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
			return mockRecognition();
		}

		ChatModel chatModel = chatModelProvider.getIfAvailable();
		if (chatModel == null) {
			return mockRecognition();
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
			throw new AppException("AI_ERROR", HttpStatus.BAD_GATEWAY, "AI 识别失败，请检查 DeepSeek 配置与网络");
		}
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
		String text = Objects.requireNonNull(response.getResult().getOutput().getContent());
		return parseRecognition(text);
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
		catch (JsonProcessingException e) {
			throw new AppException("AI_PARSE_ERROR", HttpStatus.BAD_GATEWAY, "AI 返回内容无法解析为结构化结果");
		}
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

	private static AiRecognitionResult mockRecognition() {
		Map<StudentBehavior, Integer> behaviors = new EnumMap<>(StudentBehavior.class);
		behaviors.put(StudentBehavior.ATTENTIVE, 18);
		behaviors.put(StudentBehavior.HEAD_DOWN, 6);
		behaviors.put(StudentBehavior.SLEEPING, 1);
		behaviors.put(StudentBehavior.PHONE, 1);
		behaviors.put(StudentBehavior.DISTRACTED, 2);
		behaviors.put(StudentBehavior.OTHER, 0);

		List<StudentState> students = new ArrayList<>();
		int no = 1;
		for (var e : behaviors.entrySet()) {
			for (int i = 0; i < e.getValue(); i++) {
				students.add(new StudentState(no++, e.getKey(), null));
			}
		}
		return new AiRecognitionResult(28, behaviors, students);
	}
}
