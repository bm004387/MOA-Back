package com.buc.ysc.common.menu.mapper;

import com.buc.ysc.common.menu.vo.MenuVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MenuMapper {
    List<MenuVO> getBottomMenuList(String menuType);
    List<MenuVO> getAllMenuList();
    boolean isMenuIcon(Long filSeq);
}
