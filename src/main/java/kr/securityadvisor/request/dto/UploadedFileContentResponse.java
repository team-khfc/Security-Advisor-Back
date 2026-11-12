package kr.securityadvisor.request.dto;

/** 내려받기 전용 원문. 업로드한 원본을 변경하지 않는다. */
public record UploadedFileContentResponse(String originalName, byte[] content) {}
