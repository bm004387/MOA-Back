package com.buc.moa.common.menu.vo;

import com.buc.moa.common.vo.CommonVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 메뉴 조회 결과와 시스템 감사 컬럼을 보관하는 VO입니다. */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuVO extends CommonVO {
    private String menuId;
    private String menuName;
    private String upperMenuId;
    private Integer menuLevel;
    private Integer sortOrder;
    private String menuType;
    private Long icoFilSeq;
    private String programCode;
    private String programUrl;
}
