package com.example.classroom_monitor.exception;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {

	private final String code;
	private final HttpStatus status;

	public AppException(String code, HttpStatus status, String message) {
		super(message);
		this.code = code;
		this.status = status;
	}

	public String getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return status;
	}
}
