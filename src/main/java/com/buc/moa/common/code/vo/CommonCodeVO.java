package com.buc.moa.common.code.vo;

import lombok.Data;

/** 공통코드 상세 조회 결과입니다. */
@Data
public class CommonCodeVO {
    private String comCd;
    private String comCdNm;
    private Integer comCdSortOrd;
    private String prntComCd;
    private String comDtlCd;
    private String comDtlNm;
    private String comDtlDesc;
    private String remk1;
    private String remk2;
    private String remk3;
    private String remk4;
    private Integer sortOrd;
}
