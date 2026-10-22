package com.buc.moa.common.menu;

import com.buc.moa.common.menu.vo.MenuVO;
import com.buc.moa.common.menu.vo.MenuIconResourceVO;
import com.buc.moa.common.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    @GetMapping(value = "/bottom", produces = "application/json; charset=UTF-8")
    public List<MenuVO> getBottomMenuList() {
        return menuService.getBottomMenuList();
    }

    @GetMapping(value = "/all", produces = "application/json; charset=UTF-8")
    public List<MenuVO> getAllMenuList() {
        return menuService.getAllMenuList();
    }

    /** 로그인된 사용자에게 메뉴에 연결된 아이콘 이미지를 반환합니다. */
    @GetMapping("/icons/{filSeq}")
    public ResponseEntity<Resource> getMenuIcon(@PathVariable Long filSeq) {
        MenuIconResourceVO icon = menuService.getMenuIcon(filSeq);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.contentType()))
                .header("Cache-Control", "private, max-age=86400")
                .body(new FileSystemResource(icon.path()));
    }
}
