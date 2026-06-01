package com.example.classroom_monitor.model;

import java.util.Map;

public record AnalysisMetrics(
		double focusScore,
		double attentiveRate,
		Map<StudentBehavior, Double> behaviorRates,
		String summary
) {
}
