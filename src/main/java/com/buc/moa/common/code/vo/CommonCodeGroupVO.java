package com.buc.moa.common.code.vo;

import lombok.Data;

/** 공통코드 분류 계층 조회 결과입니다. */
@Data
public class CommonCodeGroupVO {
    private String comCd;
    private String comCdNm;
    private String comCdDesc;
    private String prntComCd;
    private Integer sortOrd;
}
