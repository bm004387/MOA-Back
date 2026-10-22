package com.buc.moa.file.service.impl;

import com.buc.moa.file.mapper.FileMapper;
import com.buc.moa.util.CommonCodeUtil;
import com.buc.moa.file.service.FileStorageService;
import com.buc.moa.file.vo.StoredFile;
import com.buc.moa.util.MsgUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/** 파일 시스템에 파일을 저장하고 FIL_BAS/FIL_DTL 메타데이터를 관리합니다. */
@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private final FileMapper fileMapper;
    private final MsgUtil msgUtil;
    private final CommonCodeUtil commonCodeUtil;
    private final Path storageRoot;

    public FileStorageServiceImpl(FileMapper fileMapper, MsgUtil msgUtil,
                                  CommonCodeUtil commonCodeUtil,
                                  @Value("${file.storage.root:./uploads}") String storageRoot) {
        this.fileMapper = fileMapper;
        this.msgUtil = msgUtil;
        this.commonCodeUtil = commonCodeUtil;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public Long store(MultipartFile file, String fileType, String fileCode, String userId) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "001"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, msgUtil.getMsg("FILE", "002"));
        }

        Long fileSeq = fileMapper.nextFileSeq();
        String originalFilename = safeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalFilename);
        if (extension.isBlank()) extension = extensionFromContentType(file.getContentType());
        String originalName = nameWithoutExtension(originalFilename, extension);
        String savedName = UUID.randomUUID().toString();
        String directoryCode = normalizeDirectoryCode(fileCode);
        Path directory = storageRoot.resolve(directoryCode).normalize();
        Path savedPath = directory.resolve(savedName).normalize();
        ensureInsideRoot(savedPath);

        StoredFile storedFile = new StoredFile();
        storedFile.setFilSeq(fileSeq);
        storedFile.setFilDtlSeq(1L);
        storedFile.setFilTyp(fileType);
        storedFile.setFilCd(fileCode);
        storedFile.setFilPth(directory.toString());
        storedFile.setOrgFilNm(originalName);
        storedFile.setSavFilNm(savedName);
        storedFile.setFilExt(extension);
        storedFile.setFilSz(file.getSize());
        storedFile.setContTyp(safeContentType(file.getContentType()));
        storedFile.setSystemUserId(userId);

        try {
            Files.createDirectories(directory);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, savedPath, StandardCopyOption.REPLACE_EXISTING);
            }
            int baseRows = fileMapper.insertFileBase(storedFile);
            int detailRows = fileMapper.insertFileDetail(storedFile);
            if (baseRows != 1 || detailRows != 1) {
                throw new IllegalStateException("파일 메타데이터 저장 결과가 올바르지 않습니다.");
            }
            log.info("File stored: filSeq={}, filCd={}, path={}", fileSeq, directoryCode, savedPath);
            return fileSeq;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.deleteIfExists(savedPath);
                Files.deleteIfExists(directory);
            } catch (IOException ignored) {
                // 원래 저장 오류를 보존합니다.
            }
            if (exception instanceof ResponseStatusException statusException) throw statusException;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    msgUtil.getMsg("FILE", "003"), exception);
        }
    }

    @Override
    public StoredFile metadata(Long fileSeq) {
        StoredFile file = fileMapper.selectFile(fileSeq);
        if (file == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("FILE", "004"));
        }
        return file;
    }

    @Override
    public Path pathOf(StoredFile file) {
        Path directory = Path.of(file.getFilPth()).toAbsolutePath().normalize();
        Path path = directory.resolve(file.getSavFilNm()).normalize();
        if (!Files.isRegularFile(path) && file.getFilExt() != null && !file.getFilExt().isBlank()) {
            // 이전 데이터는 SAV_FIL_NM에 확장자를 포함해 저장했으므로 구 데이터도 조회합니다.
            path = directory.resolve(file.getSavFilNm() + file.getFilExt()).normalize();
        }
        if (!Files.isRegularFile(path)) {
            String directoryCode = directoryCodeFor(file.getFilCd());
            if (directoryCode != null) {
                Path codeDirectory = storageRoot.resolve(directoryCode).normalize();
                Path codePath = codeDirectory.resolve(file.getSavFilNm()).normalize();
                if (!Files.isRegularFile(codePath) && file.getFilExt() != null && !file.getFilExt().isBlank()) {
                    codePath = codeDirectory.resolve(file.getSavFilNm() + file.getFilExt()).normalize();
                }
                if (Files.isRegularFile(codePath)) path = codePath;
            }
        }
        ensureInsideRoot(path);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("FILE", "004"));
        }
        return path;
    }

    @Override
    @Transactional
    public void delete(Long fileSeq) {
        if (fileSeq == null) return;
        StoredFile file = fileMapper.selectFile(fileSeq);
        if (file == null) return;
        Path path = pathOf(file);
        try {
            Files.deleteIfExists(path);
            // File directories are shared by file code (for example 2=profile image).
            // Keep the directory while it may contain other files.
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    msgUtil.getMsg("FILE", "006"), exception);
        }
        fileMapper.deleteFileDetails(fileSeq);
        fileMapper.deleteFileBase(fileSeq);
    }

    private void ensureInsideRoot(Path path) {
        if (!path.normalize().startsWith(storageRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "005"));
        }
    }

    private String normalizeDirectoryCode(String fileCode) {
        if (fileCode == null || !fileCode.matches("[0-9]{1,20}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("FILE", "005"));
        }
        return fileCode;
    }

    private String directoryCodeFor(String fileCode) {
        if (fileCode != null && fileCode.matches("[0-9]{1,20}")) return fileCode;
        // 이전 배포의 프로필 코드를 새 디렉터리 코드에 연결합니다.
        if ("PROFILE".equalsIgnoreCase(fileCode)) return "002";
        return null;
    }

    private String safeOriginalName(String name) {
        String value = name == null ? "upload" : Path.of(name).getFileName().toString();
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String extension = name.substring(dot).replaceAll("[^A-Za-z0-9.]", "").toLowerCase(Locale.ROOT);
        return extension.length() > 20 ? extension.substring(0, 20) : extension;
    }

    private String nameWithoutExtension(String name, String extension) {
        if (extension == null || extension.isBlank() || !name.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return name;
        }
        return name.substring(0, name.length() - extension.length());
    }

    private String extensionFromContentType(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/heic" -> ".heic";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }

    private String safeContentType(String contentType) {
        String value = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
        try {
            return commonCodeUtil.getDetailCode("CONT_TYP", value);
        } catch (IllegalArgumentException exception) {
            return commonCodeUtil.getDetailCode("CONT_TYP", "application/octet-stream");
        }
    }
}
