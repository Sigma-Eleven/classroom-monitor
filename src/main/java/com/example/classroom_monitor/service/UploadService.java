package com.example.classroom_monitor.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.classroom_monitor.config.AppProperties;
import com.example.classroom_monitor.exception.AppException;
import com.example.classroom_monitor.model.UploadRecord;
import com.example.classroom_monitor.store.InMemorySessionStore;

@Service
public class UploadService {

	private final InMemorySessionStore store;
	private final AppProperties properties;

	public UploadService(InMemorySessionStore store, AppProperties properties) {
		this.store = store;
		this.properties = properties;
	}

	public UploadRecord uploadImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new AppException("EMPTY_FILE", HttpStatus.BAD_REQUEST, "请选择要上传的图片文件");
		}

		long maxBytes = properties.getUpload().getMaxSize().toBytes();
		if (file.getSize() > maxBytes) {
			throw new AppException("FILE_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大，请选择更小的图片");
		}

		String contentType = file.getContentType();
		if (!StringUtils.hasText(contentType) || !properties.getUpload().getAllowedContentTypes().contains(contentType)) {
			throw new AppException("INVALID_TYPE", HttpStatus.BAD_REQUEST, "文件格式不支持，请上传 JPG/PNG/WEBP 图片");
		}

		String originalFilename = file.getOriginalFilename();
		String ext = extensionOf(originalFilename);
		if (!properties.getUpload().getAllowedExtensions().contains(ext)) {
			throw new AppException("INVALID_EXT", HttpStatus.BAD_REQUEST, "文件后缀不支持，请上传 JPG/PNG/WEBP 图片");
		}

		String uploadId = UUID.randomUUID().toString().replace("-", "");
		String safeOriginal = StringUtils.hasText(originalFilename) ? Paths.get(originalFilename).getFileName().toString() : ("upload." + ext);
		String contentHash = sha256Hex(file);
		String extByType = extensionByContentType(contentType);
		String storedExt = StringUtils.hasText(extByType) ? extByType : ext;
		if (!StringUtils.hasText(storedExt)) {
			storedExt = "img";
		}
		String storedFilename = contentHash + "." + storedExt;

		Path uploadDir = Paths.get(properties.getUpload().getDir()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(uploadDir);
		}
		catch (IOException e) {
			throw new AppException("IO_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "创建上传目录失败");
		}

		Path target = uploadDir.resolve(storedFilename).normalize();
		try {
			if (Files.notExists(target)) {
				file.transferTo(target);
			}
		}
		catch (IOException e) {
			throw new AppException("IO_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "保存图片失败");
		}

		UploadRecord record = new UploadRecord(
				uploadId,
				Instant.now(),
				safeOriginal,
				contentType,
				file.getSize(),
				target.toString()
		);
		store.putUpload(record);
		return record;
	}

	private static String sha256Hex(MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0) {
				md.update(buf, 0, n);
			}
			return HexFormat.of().formatHex(md.digest());
		}
		catch (Exception e) {
			throw new AppException("HASH_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "计算文件指纹失败");
		}
	}

	private static String extensionByContentType(String contentType) {
		if (!StringUtils.hasText(contentType)) {
			return "";
		}
		return switch (contentType) {
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			case "image/webp" -> "webp";
			default -> "";
		};
	}

	private static String extensionOf(String filename) {
		if (!StringUtils.hasText(filename)) {
			return "";
		}
		String name = Paths.get(filename).getFileName().toString();
		int idx = name.lastIndexOf('.');
		if (idx < 0 || idx == name.length() - 1) {
			return "";
		}
		return name.substring(idx + 1).toLowerCase(Locale.ROOT);
	}
}
