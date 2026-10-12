package com.buc.ysc.common.menu.service.impl;

import com.buc.ysc.common.menu.service.MenuService;
import com.buc.ysc.common.menu.mapper.MenuMapper;
import com.buc.ysc.common.menu.vo.MenuVO;
import java.util.List;
import org.springframework.stereotype.Service;

/** 메뉴 데이터를 mapper를 통해 조회합니다. */
@Service
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;

    public MenuServiceImpl(MenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    @Override
    public List<MenuVO> getBottomMenuList() {
        return menuMapper.getBottomMenuList("002");
    }

    @Override
    public List<MenuVO> getAllMenuList() {
        return menuMapper.getAllMenuList();
    }
}
