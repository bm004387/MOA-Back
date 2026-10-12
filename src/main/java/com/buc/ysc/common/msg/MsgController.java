package com.buc.ysc.common.msg;

import com.buc.ysc.common.msg.service.MsgService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/msg")
public class MsgController {

    private final MsgService msgService;

    @GetMapping(value = "/all",produces = "application/json; charset=UTF-8")
    public Map<String, String> getAllMsg() {
        return msgService.getAllMsg();
    }
}
