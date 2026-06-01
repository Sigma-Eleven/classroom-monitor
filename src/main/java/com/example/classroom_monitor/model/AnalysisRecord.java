package com.example.classroom_monitor.model;

import java.time.Instant;

public record AnalysisRecord(
		String analysisId,
		Instant createdAt,
		UploadRecord upload,
		AiRecognitionResult recognition,
		AnalysisMetrics metrics
) {
}
