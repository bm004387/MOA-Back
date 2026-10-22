package com.buc.moa.common.menu.service.impl;

import com.buc.moa.common.menu.service.MenuService;
import com.buc.moa.common.menu.mapper.MenuMapper;
import com.buc.moa.common.menu.vo.MenuIconResourceVO;
import com.buc.moa.common.menu.vo.MenuVO;
import com.buc.moa.file.service.FileStorageService;
import com.buc.moa.file.vo.StoredFile;
import com.buc.moa.util.CommonCodeUtil;
import com.buc.moa.util.MsgUtil;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 메뉴 데이터를 mapper를 통해 조회합니다. */
@Service
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final FileStorageService fileStorageService;
    private final CommonCodeUtil commonCodeUtil;
    private final MsgUtil msgUtil;

    public MenuServiceImpl(MenuMapper menuMapper, FileStorageService fileStorageService,
                           CommonCodeUtil commonCodeUtil, MsgUtil msgUtil) {
        this.menuMapper = menuMapper;
        this.fileStorageService = fileStorageService;
        this.commonCodeUtil = commonCodeUtil;
        this.msgUtil = msgUtil;
    }

    @Override
    public List<MenuVO> getBottomMenuList() {
        return menuMapper.getBottomMenuList("002");
    }

    @Override
    public List<MenuVO> getAllMenuList() {
        return menuMapper.getAllMenuList();
    }

    @Override
    public MenuIconResourceVO getMenuIcon(Long filSeq) {
        if (filSeq == null || !menuMapper.isMenuIcon(filSeq)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("FILE", "004"));
        }

        StoredFile file = fileStorageService.metadata(filSeq);
        String contentType = commonCodeUtil.getCodeName("CONT_TYP", file.getContTyp());
        if (!contentType.toLowerCase().startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, msgUtil.getMsg("FILE", "004"));
        }

        return new MenuIconResourceVO(fileStorageService.pathOf(file), contentType);
    }
}
