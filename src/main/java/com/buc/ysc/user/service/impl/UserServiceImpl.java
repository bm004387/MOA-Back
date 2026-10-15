package com.buc.ysc.user.service.impl;

import com.buc.ysc.file.service.FileStorageService;
import com.buc.ysc.file.vo.StoredFile;
import com.buc.ysc.security.SessionManager;
import com.buc.ysc.security.UserSession;
import com.buc.ysc.user.mapper.UserMapper;
import com.buc.ysc.user.service.UserService;
import com.buc.ysc.user.vo.request.AddressChangeRequest;
import com.buc.ysc.user.vo.request.NicknameChangeRequest;
import com.buc.ysc.user.vo.request.PasswordChangeRequest;
import com.buc.ysc.user.vo.request.PasswordVerifyRequest;
import com.buc.ysc.user.vo.request.PinChangeRequest;
import com.buc.ysc.user.vo.request.UserVO;
import com.buc.ysc.user.vo.response.UserProfileResponse;
import com.buc.ysc.util.CommonCodeUtil;
import com.buc.ysc.util.MsgUtil;
import java.util.Map;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** 사용자 업무 규칙과 공통코드·메시지 조회를 구현합니다. */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final SessionManager sessionManager;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final MsgUtil msgUtil;
    private final CommonCodeUtil commonCodeUtil;

    public UserServiceImpl(UserMapper userMapper, SessionManager sessionManager,
            PasswordEncoder passwordEncoder, FileStorageService fileStorageService,
            MsgUtil msgUtil, CommonCodeUtil commonCodeUtil) {
        this.userMapper = userMapper;
        this.sessionManager = sessionManager;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.msgUtil = msgUtil;
        this.commonCodeUtil = commonCodeUtil;
    }

    private UserSession requireSession(String authorizationHeader) {
        String token = authorizationHeader != null && authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : null;
        UserSession session = token == null ? null : sessionManager.getSession(token);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, msgUtil.getMsg("MYINFO", "009"));
        }
        return session;
    }

    @Override
    public UserProfileResponse getMyProfile(String authorizationHeader) {
        UserSession session = requireSession(authorizationHeader);
        UserVO user = findUser(session.usrId());
        return new UserProfileResponse(session.usrId(), session.usrNm(),
                valueOrDatabase(session.hpNo(), user.getHpNo()),
                valueOrDatabase(session.adr(), user.getAdr()),
                valueOrDatabase(session.dtlAdr(), user.getDtlAdr()),
                user.getPrflImgFilSeq() == null ? null : "/api/user/me/profile-image");
    }

    @Override
    @Transactional
    public Map<String, Object> changePassword(String authorizationHeader,
            PasswordChangeRequest body) {
        UserSession session = requireSession(authorizationHeader);
        UserVO user = userMapper.selectByUsrId(session.usrId());
        if (user == null || body.currentPassword() == null
                || !passwordEncoder.matches(body.currentPassword(), user.getPwd())) {
            return Map.of("success", false, "message", msgUtil.getMsg("MYINFO", "001"));
        }
        if (body.newPassword() == null || body.newPassword().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("MYINFO", "002"));
        }
        if (!body.newPassword().equals(body.confirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("COMMON", "003"));
        }
        UserVO update = new UserVO();
        update.setUsrId(session.usrId());
        update.setPwd(passwordEncoder.encode(body.newPassword()));
        update.setSystemUserId(session.usrId());
        if (userMapper.updatePassword(update) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    msgUtil.getMsg("MYINFO", "008"));
        }
        sessionManager.deleteOtherSessionsForUser(session.usrId(),
                authorizationHeader.substring("Bearer ".length()));
        return Map.of("success", true, "message", msgUtil.getMsg("MYINFO", "003"));
    }

    @Override
    public Map<String, Object> verifyCurrentPassword(String authorizationHeader,
            PasswordVerifyRequest body) {
        UserSession session = requireSession(authorizationHeader);
        UserVO user = userMapper.selectByUsrId(session.usrId());
        boolean valid = user != null && body.currentPassword() != null
                && passwordEncoder.matches(body.currentPassword(), user.getPwd());
        return Map.of("valid", valid, "message", msgUtil.getMsg("MYINFO", valid ? "014" : "001"));
    }

    @Override
    @Transactional
    public Map<String, String> changePin(String authorizationHeader, PinChangeRequest body) {
        UserSession session = requireSession(authorizationHeader);
        UserVO update = new UserVO();
        update.setUsrId(session.usrId());
        update.setPinPwd(passwordEncoder.encode(body.pin()));
        update.setSystemUserId(session.usrId());
        if (userMapper.updatePinPassword(update) != 1) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    msgUtil.getMsg("MYINFO", "010"));
        }
        sessionManager.deleteOtherSessionsForUser(session.usrId(),
                authorizationHeader.substring("Bearer ".length()));
        return Map.of("message", msgUtil.getMsg("AUTH", "007"));
    }

    @Override
    @Transactional
    public Map<String, String> changeAddress(String authorizationHeader, AddressChangeRequest body) {
        UserSession session = requireSession(authorizationHeader);
        if (body.adr() == null || body.adr().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("COMMON", "007"));
        }
        String address = body.adr().trim();
        String detail = body.dtlAdr() == null ? "" : body.dtlAdr().trim();
        UserVO update = new UserVO();
        update.setUsrId(session.usrId());
        update.setAdr(address);
        update.setDtlAdr(detail);
        update.setSystemUserId(session.usrId());
        userMapper.updateAddress(update);
        sessionManager.updateAddress(authorizationHeader.substring(7), address, detail);
        return Map.of("message", msgUtil.getMsg("MYINFO", "004"));
    }

    @Override
    @Transactional
    public Map<String, String> changeNickname(String authorizationHeader, NicknameChangeRequest body) {
        UserSession session = requireSession(authorizationHeader);
        String nickname = body.nckNm().trim();
        UserVO update = new UserVO();
        update.setUsrId(session.usrId());
        update.setNckNm(nickname);
        update.setSystemUserId(session.usrId());
        if (userMapper.updateNickname(update) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("MYINFO", "010"));
        }
        sessionManager.updateDisplayName(authorizationHeader.substring(7), nickname);
        return Map.of("nckNm", nickname);
    }

    @Override
    @Transactional
    public Map<String, String> saveProfileImage(String authorizationHeader, MultipartFile file,
            String requestedFileType, String requestedFileCode) {
        UserSession session = requireSession(authorizationHeader);
        String fileType = "002";
        String fileCode = "002";
        validateFileClassification(requestedFileType, fileType);
        validateFileClassification(requestedFileCode, fileCode);
        String contentType = file == null ? null : file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msgUtil.getMsg("MYINFO", "011"));
        }
        try {
            commonCodeUtil.getDetailCode("CONT_TYP", contentType.toLowerCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    msgUtil.getMsg("MYINFO", "011"), exception);
        }
        UserVO user = findUser(session.usrId());
        Long newFileSeq = fileStorageService.store(file, fileType, fileCode, session.usrId());
        UserVO update = new UserVO();
        update.setUsrId(session.usrId());
        update.setPrflImgFilSeq(newFileSeq);
        update.setSystemUserId(session.usrId());
        if (userMapper.updateProfileImageFileSeq(update) != 1) {
            fileStorageService.delete(newFileSeq);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("MYINFO", "010"));
        }
        return Map.of("message", msgUtil.getMsg("MYINFO", "005"),
                "profileImageUrl", "/api/user/me/profile-image");
    }

    @Override
    public StoredFile getProfileImage(String authorizationHeader) {
        UserSession session = requireSession(authorizationHeader);
        UserVO user = findUser(session.usrId());
        if (user.getPrflImgFilSeq() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("MYINFO", "013"));
        }
        return fileStorageService.metadata(user.getPrflImgFilSeq());
    }

    @Override
    public String getContentType(String storedCode) {
        return commonCodeUtil.getCodeName("CONT_TYP", storedCode);
    }

    @Override
    public Path getFilePath(StoredFile file) {
        return fileStorageService.pathOf(file);
    }

    private UserVO findUser(String usrId) {
        UserVO user = userMapper.selectByUsrId(usrId);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("MYINFO", "010"));
        }
        return user;
    }

    private void validateFileClassification(String requestedCode, String expectedCode) {
        if (requestedCode != null && !requestedCode.equals(expectedCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "파일 분류 코드가 올바르지 않습니다.");
        }
    }

    private String valueOrDatabase(String sessionValue, String databaseValue) {
        return sessionValue == null || sessionValue.isBlank() ? databaseValue : sessionValue;
    }
}
