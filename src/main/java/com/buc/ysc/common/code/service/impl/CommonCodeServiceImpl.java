package com.buc.ysc.common.code.service.impl;

import com.buc.ysc.common.code.service.CommonCodeService;
import com.buc.ysc.common.code.vo.CommonCodeVO;
import com.buc.ysc.util.CommonCodeUtil;
import java.util.List;
import org.springframework.stereotype.Service;

/** 공통코드 조회 및 Redis 캐시 갱신을 위임합니다. */
@Service
public class CommonCodeServiceImpl implements CommonCodeService {

    private final CommonCodeUtil commonCodeUtil;

    public CommonCodeServiceImpl(CommonCodeUtil commonCodeUtil) {
        this.commonCodeUtil = commonCodeUtil;
    }

    @Override
    public List<CommonCodeVO> getAllCodes() {
        return commonCodeUtil.getAllCodes();
    }

    @Override
    public List<CommonCodeVO> refreshCodes() {
        return commonCodeUtil.refreshCodes();
    }
}
