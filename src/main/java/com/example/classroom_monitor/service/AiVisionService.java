package com.example.classroom_monitor.service;

import com.example.classroom_monitor.model.AiRecognitionResult;
import com.example.classroom_monitor.model.UploadRecord;

public interface AiVisionService {
	AiRecognitionResult recognize(UploadRecord upload);
}
