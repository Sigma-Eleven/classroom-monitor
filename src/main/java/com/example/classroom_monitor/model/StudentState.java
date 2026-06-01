package com.example.classroom_monitor.model;

public record StudentState(
		int studentNo,
		StudentBehavior behavior,
		Double confidence
) {
}
