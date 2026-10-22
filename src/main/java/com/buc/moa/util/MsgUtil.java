package com.buc.moa.util;

import com.buc.moa.common.msg.mapper.MsgMapper;
import com.buc.moa.common.msg.vo.MsgVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MsgUtil {

    private static final String MSG_KEY = "MSG:ALL";

    private final MsgMapper msgMapper;
    private final RedisTemplate<String, String> msgRedisTemplate;

    /**
     * 메시지 1건 조회
     *
     * @param menuId 메뉴 ID
     * @param msgCd  메시지 코드
     * @return 메시지
     */
    public String getMsg(String menuId, String msgCd) {

        String field = menuId + ":" + msgCd;

        // 1. Redis에서 메시지 조회
        Object redisMsg = msgRedisTemplate.opsForHash().get(MSG_KEY, field);

        if (redisMsg != null && !redisMsg.toString().isBlank()) {
            return redisMsg.toString();
        }

        // 2. 전체 메시지가 Redis에 없으면 DB에서 전체 적재
        if (!Boolean.TRUE.equals(msgRedisTemplate.hasKey(MSG_KEY))) {
            loadMsgToRedis();

            // 3. 다시 조회
            redisMsg = msgRedisTemplate.opsForHash().get(MSG_KEY, field);

            if (redisMsg != null && !redisMsg.toString().isBlank()) {
                return redisMsg.toString();
            }
        }

        // 4. 메시지가 없으면 코드 반환
        return menuId + "_" + msgCd;
    }

    /**
     * 전체 메시지 조회
     * 화면에서 사용하는 API에서 호출
     * @return 전체 메시지
     */
    public Map<String, String> getAllMsg() {
        // DB를 다시 적재해 운영 중 추가/수정된 메시지가 화면에 반영되도록 합니다.
        loadMsgToRedis();
        return convertToStringMap(msgRedisTemplate.opsForHash().entries(MSG_KEY));
    }

    /**
     * DB의 전체 메시지를 Redis에 적재
     */
    public void loadMsgToRedis() {

        List<MsgVO> msgList = msgMapper.getMsgList();

        if (msgList == null || msgList.isEmpty()) {
            return;
        }

        Map<String, String> redisData = new LinkedHashMap<>();

        for (MsgVO msg : msgList) {

            String field = msg.getMenuId() + ":" + msg.getMsgCd();

            redisData.put(field, msg.getMsgCn());
        }

        // 기존 전체 메시지 삭제
        msgRedisTemplate.delete(MSG_KEY);

        // 전체 메시지 저장
        msgRedisTemplate.opsForHash().putAll(MSG_KEY, redisData);
    }

    /**
     * Redis Object Map을 String Map으로 변환
     */
    private Map<String, String> convertToStringMap(Map<Object, Object> redisMap) {

        Map<String, String> msgMap = new LinkedHashMap<>();

        if (redisMap == null || redisMap.isEmpty()) {
            return msgMap;
        }

        for (Map.Entry<Object, Object> entry : redisMap.entrySet()) {
            msgMap.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
        }

        return msgMap;
    }
}
