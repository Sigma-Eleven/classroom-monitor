package com.example.classroom_monitor.model;

import java.util.List;
import java.util.Map;

public record AiRecognitionResult(
		int totalStudents,
		Map<StudentBehavior, Integer> behaviors,
		List<StudentState> students
) {
}
