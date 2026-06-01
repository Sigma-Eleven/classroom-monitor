package com.example.classroom_monitor.service;

import com.example.classroom_monitor.exception.AppException;
import com.example.classroom_monitor.model.AiRecognitionResult;
import com.example.classroom_monitor.model.AnalysisMetrics;
import com.example.classroom_monitor.model.AnalysisRecord;
import com.example.classroom_monitor.model.SessionStats;
import com.example.classroom_monitor.model.StudentBehavior;
import com.example.classroom_monitor.model.UploadRecord;
import com.example.classroom_monitor.store.InMemorySessionStore;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ClassroomMonitorService {

	private final UploadService uploadService;
	private final AiVisionService aiVisionService;
	private final AnalysisService analysisService;
	private final InMemorySessionStore store;

	public ClassroomMonitorService(
			UploadService uploadService,
			AiVisionService aiVisionService,
			AnalysisService analysisService,
			InMemorySessionStore store
	) {
		this.uploadService = uploadService;
		this.aiVisionService = aiVisionService;
		this.analysisService = analysisService;
		this.store = store;
	}

	public UploadRecord upload(MultipartFile file) {
		return uploadService.uploadImage(file);
	}

	public UploadRecord getUpload(String uploadId) {
		return store.findUpload(uploadId)
				.orElseThrow(() -> new AppException("UPLOAD_NOT_FOUND", HttpStatus.NOT_FOUND, "未找到上传记录，请重新上传"));
	}

	public AnalysisRecord analyze(String uploadId) {
		UploadRecord upload = store.findUpload(uploadId)
				.orElseThrow(() -> new AppException("UPLOAD_NOT_FOUND", HttpStatus.NOT_FOUND, "未找到上传记录，请重新上传"));

		AiRecognitionResult recognition = aiVisionService.recognize(upload);
		AnalysisMetrics metrics = analysisService.analyze(recognition);

		String analysisId = UUID.randomUUID().toString().replace("-", "");
		AnalysisRecord record = new AnalysisRecord(analysisId, Instant.now(), upload, recognition, metrics);
		store.putAnalysis(record);
		return record;
	}

	public AnalysisRecord uploadAndAnalyze(MultipartFile file) {
		UploadRecord upload = upload(file);
		return analyze(upload.uploadId());
	}

	public AnalysisRecord getAnalysis(String analysisId) {
		return store.findAnalysis(analysisId)
				.orElseThrow(() -> new AppException("ANALYSIS_NOT_FOUND", HttpStatus.NOT_FOUND, "未找到识别结果"));
	}

	public List<AnalysisRecord> listAnalyses() {
		return store.listAnalysesNewestFirst();
	}

	public SessionStats getSessionStats() {
		List<AnalysisRecord> list = listAnalyses();
		if (list.isEmpty()) {
			return new SessionStats(0, 0.0, Map.of());
		}

		double avg = list.stream().mapToDouble(r -> r.metrics().focusScore()).average().orElse(0.0);
		Map<StudentBehavior, Integer> totals = new EnumMap<>(StudentBehavior.class);
		for (StudentBehavior behavior : StudentBehavior.values()) {
			totals.put(behavior, 0);
		}
		for (AnalysisRecord r : list) {
			for (StudentBehavior behavior : StudentBehavior.values()) {
				int v = r.recognition().behaviors().getOrDefault(behavior, 0);
				totals.put(behavior, totals.getOrDefault(behavior, 0) + v);
			}
		}
		return new SessionStats(list.size(), Math.round(avg * 10.0) / 10.0, totals);
	}
}
