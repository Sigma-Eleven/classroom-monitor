package com.example.classroom_monitor.model;

import java.util.Map;

public record SessionStats(
		int totalAnalyses,
		double averageFocusScore,
		Map<StudentBehavior, Integer> totalBehaviorCounts
) {
}
