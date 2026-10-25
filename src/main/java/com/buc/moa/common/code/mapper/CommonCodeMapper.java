package com.buc.moa.common.code.mapper;

import com.buc.moa.common.code.vo.CommonCodeVO;
import com.buc.moa.common.code.vo.CommonCodeGroupVO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/** 공통코드 분류와 상세 값을 조회합니다. */
@Mapper
public interface CommonCodeMapper {

    /** 사용 중인 공통코드 상세 목록을 조회합니다. */
    List<CommonCodeVO> selectActiveCodes();

    /** 상위 공통코드가 연결된 사용 중 분류 목록을 조회합니다. */
    List<CommonCodeGroupVO> selectActiveGroups();

    /** 분류코드와 상세코드로 공통코드 한 건을 조회합니다. */
    CommonCodeVO selectCode(
            String comCd,
            String comDtlCd);
}
