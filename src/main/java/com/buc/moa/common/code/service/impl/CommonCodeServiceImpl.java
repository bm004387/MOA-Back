package com.buc.moa.common.code.service.impl;

import com.buc.moa.common.code.service.CommonCodeService;
import com.buc.moa.common.code.mapper.CommonCodeMapper;
import com.buc.moa.common.code.vo.CommonCodeVO;
import com.buc.moa.common.code.vo.CommonCodeGroupVO;
import com.buc.moa.util.CommonCodeUtil;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 공통코드 조회 및 Redis 캐시 갱신을 위임합니다. */
@Service
public class CommonCodeServiceImpl implements CommonCodeService {

    private final CommonCodeUtil commonCodeUtil;
    private final CommonCodeMapper commonCodeMapper;
    private final String deploymentToken;

    public CommonCodeServiceImpl(
            CommonCodeUtil commonCodeUtil,
            CommonCodeMapper commonCodeMapper,
            @Value("${app.security.deploy-token:}") String deploymentToken) {
        this.commonCodeUtil = commonCodeUtil;
        this.commonCodeMapper = commonCodeMapper;
        this.deploymentToken = deploymentToken;
    }

    @Override
    public List<CommonCodeVO> getAllCodes() {
        return commonCodeUtil.getAllCodes();
    }

    @Override
    public List<CommonCodeGroupVO> getAllGroups() {
        return commonCodeMapper.selectActiveGroups();
    }

    @Override
    public List<CommonCodeVO> refreshCodes(String providedToken) {
        if (!hasValidDeploymentToken(providedToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공통코드 갱신 권한이 없습니다.");
        }
        return commonCodeUtil.refreshCodes();
    }

    private boolean hasValidDeploymentToken(String providedToken) {
        if (deploymentToken == null || deploymentToken.isBlank()
                || providedToken == null || providedToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                deploymentToken.getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
