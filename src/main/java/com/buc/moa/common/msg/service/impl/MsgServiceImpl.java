package com.buc.moa.common.msg.service.impl;

import com.buc.moa.common.msg.service.MsgService;
import com.buc.moa.util.MsgUtil;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 메시지 유틸을 호출해 전체 메시지를 반환합니다. */
@Service
public class MsgServiceImpl implements MsgService {

    private final MsgUtil msgUtil;

    public MsgServiceImpl(MsgUtil msgUtil) {
        this.msgUtil = msgUtil;
    }

    @Override
    public Map<String, String> getAllMsg() {
        return msgUtil.getAllMsg();
    }
}
