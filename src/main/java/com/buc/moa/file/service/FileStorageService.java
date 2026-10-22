package com.buc.moa.file.service;

import com.buc.moa.file.vo.StoredFile;

import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/** 공통 파일 저장소 서비스 계약. */
public interface FileStorageService {

    Long store(MultipartFile file, String fileType, String fileCode, String userId);

    StoredFile metadata(Long fileSeq);

    Path pathOf(StoredFile file);

    void delete(Long fileSeq);
}
