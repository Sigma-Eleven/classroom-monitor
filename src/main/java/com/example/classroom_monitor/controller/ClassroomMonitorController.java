package com.example.classroom_monitor.controller;

import com.example.classroom_monitor.dto.ApiResponse;
import com.example.classroom_monitor.exception.AppException;
import com.example.classroom_monitor.model.AnalysisRecord;
import com.example.classroom_monitor.model.SessionStats;
import com.example.classroom_monitor.model.UploadRecord;
import com.example.classroom_monitor.service.ClassroomMonitorService;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class ClassroomMonitorController {

	private final ClassroomMonitorService service;

	public ClassroomMonitorController(ClassroomMonitorService service) {
		this.service = service;
	}

	@PostMapping("/upload")
	public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
		UploadRecord upload = service.upload(file);
		return ApiResponse.ok(Map.of(
				"upload", upload,
				"previewUrl", "/api/v1/uploads/" + upload.uploadId() + "/file"
		));
	}

	@PostMapping("/analyze")
	public ApiResponse<Map<String, Object>> uploadAndAnalyze(@RequestParam("file") MultipartFile file) {
		AnalysisRecord record = service.uploadAndAnalyze(file);
		return ApiResponse.ok(Map.of(
				"analysis", record,
				"previewUrl", "/api/v1/uploads/" + record.upload().uploadId() + "/file"
		));
	}

	@PostMapping("/analyze/{uploadId}")
	public ApiResponse<AnalysisRecord> analyze(@PathVariable String uploadId) {
		return ApiResponse.ok(service.analyze(uploadId));
	}

	@GetMapping("/results/{analysisId}")
	public ApiResponse<AnalysisRecord> getResult(@PathVariable String analysisId) {
		return ApiResponse.ok(service.getAnalysis(analysisId));
	}

	@GetMapping("/results")
	public ApiResponse<List<AnalysisRecord>> listResults() {
		return ApiResponse.ok(service.listAnalyses());
	}

	@GetMapping("/stats")
	public ApiResponse<SessionStats> stats() {
		return ApiResponse.ok(service.getSessionStats());
	}

	@GetMapping("/uploads/{uploadId}/file")
	public ResponseEntity<Resource> preview(@PathVariable String uploadId) {
		UploadRecord upload = service.getUpload(uploadId);
		return buildFileResponse(upload);
	}

	private ResponseEntity<Resource> buildFileResponse(UploadRecord upload) {
		if (upload == null || !StringUtils.hasText(upload.absolutePath())) {
			throw new AppException("UPLOAD_NOT_FOUND", HttpStatus.NOT_FOUND, "未找到图片文件");
		}
		FileSystemResource resource = new FileSystemResource(upload.absolutePath());
		if (!resource.exists()) {
			throw new AppException("FILE_NOT_FOUND", HttpStatus.NOT_FOUND, "图片文件不存在");
		}
		MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
		if (StringUtils.hasText(upload.contentType())) {
			mediaType = MediaType.parseMediaType(upload.contentType());
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, "no-store")
				.contentType(mediaType)
				.body(resource);
	}
}
