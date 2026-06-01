package com.example.classroom_monitor.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

public record UploadRecord(
		String uploadId,
		Instant createdAt,
		String originalFilename,
		String contentType,
		long sizeBytes,
		@JsonIgnore
		String absolutePath
) {
}
