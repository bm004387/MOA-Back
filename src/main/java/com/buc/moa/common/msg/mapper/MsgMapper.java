package com.buc.moa.common.msg.mapper;

import com.buc.moa.common.msg.vo.MsgVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MsgMapper {

    List<MsgVO> getMsgList();
}