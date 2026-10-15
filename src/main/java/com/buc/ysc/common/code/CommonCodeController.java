package com.buc.ysc.common.code;

import com.buc.ysc.common.code.vo.CommonCodeVO;
import com.buc.ysc.common.code.service.CommonCodeService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 공통코드 조회 API입니다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/common-codes")
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    /** 화면에서 공통코드 캐시를 구성할 수 있도록 사용 중인 코드를 반환합니다. */
    @GetMapping(value = "/all", produces = "application/json; charset=UTF-8")
    public List<CommonCodeVO> getAllCodes() {
        return commonCodeService.getAllCodes();
    }

    /** DB의 사용 중 공통코드를 Redis 캐시에 다시 적재합니다. */
    @org.springframework.web.bind.annotation.PostMapping("/refresh")
    public List<CommonCodeVO> refreshCodes(
            @RequestHeader(value = "X-Deployment-Token", required = false) String deploymentToken) {
        return commonCodeService.refreshCodes(deploymentToken);
    }
}
