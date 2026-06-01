package com.example.classroom_monitor.service;

import com.example.classroom_monitor.config.AppProperties;
import com.example.classroom_monitor.model.AiRecognitionResult;
import com.example.classroom_monitor.model.AnalysisMetrics;
import com.example.classroom_monitor.model.StudentBehavior;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalysisService {

	private final AppProperties properties;

	public AnalysisService(AppProperties properties) {
		this.properties = properties;
	}

	public AnalysisMetrics analyze(AiRecognitionResult recognition) {
		int total = Math.max(1, recognition.totalStudents());

		Map<StudentBehavior, Double> rates = new EnumMap<>(StudentBehavior.class);
		for (StudentBehavior behavior : StudentBehavior.values()) {
			int count = recognition.behaviors().getOrDefault(behavior, 0);
			rates.put(behavior, count * 1.0 / total);
		}

		double attentiveRate = rates.getOrDefault(StudentBehavior.ATTENTIVE, 0.0);
		double focusScore = computeFocusScore(total, recognition.behaviors());
		String summary = buildSummary(total, recognition.behaviors(), focusScore);

		return new AnalysisMetrics(round1(focusScore), round3(attentiveRate), rates, summary);
	}

	private double computeFocusScore(int total, Map<StudentBehavior, Integer> counts) {
		double sum = 0.0;
		for (StudentBehavior behavior : StudentBehavior.values()) {
			int c = counts.getOrDefault(behavior, 0);
			double w = properties.getAnalysis().getWeights().getOrDefault(behavior.name(), 0.5);
			sum += w * c;
		}
		double score = sum / total * 100.0;
		return Math.max(0.0, Math.min(100.0, score));
	}

	private static String buildSummary(int total, Map<StudentBehavior, Integer> counts, double focusScore) {
		int attentive = counts.getOrDefault(StudentBehavior.ATTENTIVE, 0);
		int phone = counts.getOrDefault(StudentBehavior.PHONE, 0);
		int sleep = counts.getOrDefault(StudentBehavior.SLEEPING, 0);
		int distracted = counts.getOrDefault(StudentBehavior.DISTRACTED, 0);

		String level = focusScore >= 85 ? "整体专注度较高" : (focusScore >= 70 ? "整体专注度一般" : "整体专注度偏低");
		StringBuilder sb = new StringBuilder();
		sb.append(level)
				.append("，抬头听课 ").append(attentive).append("/").append(total).append("。");

		if (phone + sleep + distracted > 0) {
			sb.append(" 需关注：");
			if (phone > 0) {
				sb.append("玩手机 ").append(phone).append(" 人；");
			}
			if (sleep > 0) {
				sb.append("睡觉 ").append(sleep).append(" 人；");
			}
			if (distracted > 0) {
				sb.append("走神 ").append(distracted).append(" 人；");
			}
		}
		return sb.toString().trim();
	}

	private static double round1(double v) {
		return Math.round(v * 10.0) / 10.0;
	}

	private static double round3(double v) {
		return Math.round(v * 1000.0) / 1000.0;
	}
}
