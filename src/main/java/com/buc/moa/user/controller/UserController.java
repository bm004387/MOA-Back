package com.buc.moa.user.controller;

import com.buc.moa.file.vo.StoredFile;
import com.buc.moa.user.service.UserService;
import com.buc.moa.user.vo.request.AddressChangeRequest;
import com.buc.moa.user.vo.request.NicknameChangeRequest;
import com.buc.moa.user.vo.request.PasswordChangeRequest;
import com.buc.moa.user.vo.request.PasswordVerifyRequest;
import com.buc.moa.user.vo.request.PinChangeRequest;
import com.buc.moa.user.vo.response.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 내 정보 및 계정 설정 API의 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getMyProfile(HttpServletRequest request) {
        return userService.getMyProfile(request.getHeader("Authorization"));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            HttpServletRequest request, @Valid @RequestBody PasswordChangeRequest body) {
        return ResponseEntity.ok(userService.changePassword(request.getHeader("Authorization"), body));
    }

    @PostMapping("/me/password/verify")
    public ResponseEntity<Map<String, Object>> verifyCurrentPassword(
            HttpServletRequest request, @Valid @RequestBody PasswordVerifyRequest body) {
        return ResponseEntity.ok(userService.verifyCurrentPassword(request.getHeader("Authorization"), body));
    }

    @PutMapping("/me/pin")
    public ResponseEntity<Map<String, String>> changePin(
            HttpServletRequest request, @Valid @RequestBody PinChangeRequest body) {
        return ResponseEntity.ok(userService.changePin(request.getHeader("Authorization"), body));
    }

    @PutMapping("/me/address")
    public ResponseEntity<Map<String, String>> changeAddress(
            HttpServletRequest request, @RequestBody AddressChangeRequest body) {
        return ResponseEntity.ok(userService.changeAddress(request.getHeader("Authorization"), body));
    }

    @PutMapping("/me/nickname")
    public ResponseEntity<Map<String, String>> changeNickname(
            HttpServletRequest request, @Valid @RequestBody NicknameChangeRequest body) {
        return ResponseEntity.ok(userService.changeNickname(request.getHeader("Authorization"), body));
    }

    @PutMapping(value = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> saveProfileImage(
            HttpServletRequest request,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "filTyp", required = false) String requestedFileType,
            @RequestPart(value = "filCd", required = false) String requestedFileCode) {
        return ResponseEntity.ok(userService.saveProfileImage(
                request.getHeader("Authorization"), file, requestedFileType, requestedFileCode));
    }

    @GetMapping("/me/profile-image")
    public ResponseEntity<Resource> getProfileImage(HttpServletRequest request) {
        StoredFile file = userService.getProfileImage(request.getHeader("Authorization"));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(userService.getContentType(file.getContTyp()));
        } catch (RuntimeException invalidType) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                .body((Resource) new FileSystemResource(userService.getFilePath(file)));
    }

}
