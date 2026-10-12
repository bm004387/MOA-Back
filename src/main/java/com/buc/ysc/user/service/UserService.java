package com.buc.ysc.user.service;

import com.buc.ysc.file.vo.StoredFile;
import com.buc.ysc.user.vo.request.AddressChangeRequest;
import com.buc.ysc.user.vo.request.NicknameChangeRequest;
import com.buc.ysc.user.vo.request.PasswordChangeRequest;
import com.buc.ysc.user.vo.request.PasswordVerifyRequest;
import com.buc.ysc.user.vo.request.PinChangeRequest;
import com.buc.ysc.user.vo.response.UserProfileResponse;
import java.util.Map;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

/** 사용자 프로필 및 계정 설정 업무를 처리합니다. */
public interface UserService {

    UserProfileResponse getMyProfile(String authorizationHeader);

    Map<String, Object> changePassword(String authorizationHeader, PasswordChangeRequest request);

    Map<String, Object> verifyCurrentPassword(String authorizationHeader, PasswordVerifyRequest request);

    Map<String, String> changePin(String authorizationHeader, PinChangeRequest request);

    Map<String, String> changeAddress(String authorizationHeader, AddressChangeRequest request);

    Map<String, String> changeNickname(String authorizationHeader, NicknameChangeRequest request);

    Map<String, String> saveProfileImage(
            String authorizationHeader, MultipartFile file, String requestedFileType, String requestedFileCode);

    StoredFile getProfileImage(String authorizationHeader);

    String getContentType(String storedCode);

    Path getFilePath(StoredFile file);
}
