package com.buc.ysc.common.menu.service;

import com.buc.ysc.common.menu.vo.MenuVO;
import java.util.List;

/** 메뉴 조회 업무를 처리합니다. */
public interface MenuService {
    List<MenuVO> getBottomMenuList();

    List<MenuVO> getAllMenuList();
}
