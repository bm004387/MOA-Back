package com.buc.moa.file.vo;

import com.buc.moa.common.vo.CommonVO;

/** 공통 파일 메타데이터와 시스템 등록 정보를 담는 VO입니다. */
public class StoredFile extends CommonVO {
    private Long filSeq;
    private Long filDtlSeq;
    private String filTyp;
    private String filPth;
    private String filCd;
    private String savFilNm;
    private String orgFilNm;
    private String contTyp;
    private String filExt;
    private Long filSz;

    public Long getFilSeq() {
        return filSeq;
    }

    public void setFilSeq(Long filSeq) {
        this.filSeq = filSeq;
    }

    public Long getFilDtlSeq() {
        return filDtlSeq;
    }

    public void setFilDtlSeq(Long filDtlSeq) {
        this.filDtlSeq = filDtlSeq;
    }

    public String getFilTyp() {
        return filTyp;
    }

    public void setFilTyp(String filTyp) {
        this.filTyp = filTyp;
    }

    public String getFilPth() {
        return filPth;
    }

    public void setFilPth(String filPth) {
        this.filPth = filPth;
    }

    public String getFilCd() {
        return filCd;
    }

    public void setFilCd(String filCd) {
        this.filCd = filCd;
    }

    public String getSavFilNm() {
        return savFilNm;
    }

    public void setSavFilNm(String savFilNm) {
        this.savFilNm = savFilNm;
    }

    public String getOrgFilNm() {
        return orgFilNm;
    }

    public void setOrgFilNm(String orgFilNm) {
        this.orgFilNm = orgFilNm;
    }

    public String getContTyp() {
        return contTyp;
    }

    public void setContTyp(String contTyp) {
        this.contTyp = contTyp;
    }

    public String getFilExt() {
        return filExt;
    }

    public void setFilExt(String filExt) {
        this.filExt = filExt;
    }

    public Long getFilSz() {
        return filSz;
    }

    public void setFilSz(Long filSz) {
        this.filSz = filSz;
    }
}
