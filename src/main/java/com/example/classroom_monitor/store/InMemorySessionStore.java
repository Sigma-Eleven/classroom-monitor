package com.example.classroom_monitor.store;

import com.example.classroom_monitor.model.AnalysisRecord;
import com.example.classroom_monitor.model.UploadRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class InMemorySessionStore {

	private final ConcurrentMap<String, UploadRecord> uploads = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, AnalysisRecord> analyses = new ConcurrentHashMap<>();

	public void putUpload(UploadRecord record) {
		uploads.put(record.uploadId(), record);
	}

	public Optional<UploadRecord> findUpload(String uploadId) {
		return Optional.ofNullable(uploads.get(uploadId));
	}

	public void putAnalysis(AnalysisRecord record) {
		analyses.put(record.analysisId(), record);
	}

	public Optional<AnalysisRecord> findAnalysis(String analysisId) {
		return Optional.ofNullable(analyses.get(analysisId));
	}

	public List<AnalysisRecord> listAnalysesNewestFirst() {
		List<AnalysisRecord> list = new ArrayList<>(analyses.values());
		list.sort(Comparator.comparing(AnalysisRecord::createdAt).reversed());
		return list;
	}
}
