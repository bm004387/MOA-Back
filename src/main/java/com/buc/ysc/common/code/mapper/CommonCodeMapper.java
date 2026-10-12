package com.buc.ysc.common.code.mapper;

import com.buc.ysc.common.code.vo.CommonCodeVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 공통코드 분류와 상세 값을 조회합니다. */
@Mapper
public interface CommonCodeMapper {

    /** 사용 중인 공통코드 상세 목록을 조회합니다. */
    List<CommonCodeVO> selectActiveCodes();

    /** 분류코드와 상세코드로 공통코드 한 건을 조회합니다. */
    CommonCodeVO selectCode(
            String comCd,
            String comDtlCd);
}
