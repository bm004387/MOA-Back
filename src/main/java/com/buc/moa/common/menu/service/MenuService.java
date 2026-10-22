package com.buc.moa.common.menu.service;

import com.buc.moa.common.menu.vo.MenuVO;
import com.buc.moa.common.menu.vo.MenuIconResourceVO;
import java.util.List;

/** 메뉴 조회 업무를 처리합니다. */
public interface MenuService {
    List<MenuVO> getBottomMenuList();

    List<MenuVO> getAllMenuList();

    /** 메뉴에 등록된 아이콘 파일을 조회합니다. */
    MenuIconResourceVO getMenuIcon(Long filSeq);
}
