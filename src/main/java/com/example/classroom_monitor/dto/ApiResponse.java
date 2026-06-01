package com.example.classroom_monitor.dto;

import java.time.Instant;

public record ApiResponse<T>(
		boolean success,
		String message,
		String code,
		Instant timestamp,
		T data
) {
	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, "OK", "OK", Instant.now(), data);
	}

	public static <T> ApiResponse<T> fail(String code, String message) {
		return new ApiResponse<>(false, message, code, Instant.now(), null);
	}
}
