package com.buc.ysc.common.code.service;

import com.buc.ysc.common.code.vo.CommonCodeVO;
import java.util.List;

/** 공통코드 조회와 캐시 갱신 업무를 처리합니다. */
public interface CommonCodeService {
    List<CommonCodeVO> getAllCodes();

    List<CommonCodeVO> refreshCodes();
}
