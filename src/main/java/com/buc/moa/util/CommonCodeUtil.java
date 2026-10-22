package com.buc.moa.util;

import com.buc.moa.common.code.mapper.CommonCodeMapper;
import com.buc.moa.common.code.vo.CommonCodeVO;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/** 공통코드와 상세코드로 공통코드명 또는 설명을 조회합니다. */
@Component
public class CommonCodeUtil {

    private static final String CODE_KEY = "COM:ALL";
    private static final String NAME_FIELD = "NM:";
    private static final String DESCRIPTION_FIELD = "DESC:";
    private static final String GROUP_NAME_FIELD = "GROUP_NM:";
    private static final String GROUP_ORDER_FIELD = "GROUP_ORD:";
    private static final String DETAIL_ORDER_FIELD = "DETAIL_ORD:";

    private final CommonCodeMapper mapper;
    private final RedisTemplate<String, String> redisTemplate;

    public CommonCodeUtil(
            CommonCodeMapper mapper,
            @Qualifier("msgRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
    }

    /** 분류코드와 상세코드에 해당하는 코드값을 반환합니다. */
    public String getCodeName(String comCd, String comDtlCd) {
        CommonCodeVO code = requireCode(comCd, comDtlCd);
        return code.getComDtlNm();
    }

    /** 분류코드와 상세코드에 해당하는 한글 설명을 반환합니다. */
    public String getCodeDesc(String comCd, String comDtlCd) {
        CommonCodeVO code = requireCode(comCd, comDtlCd);
        return code.getComDtlDesc();
    }

    /** 공통코드명에 대응하는 사용 중인 상세코드를 반환합니다. */
    public String getDetailCode(String comCd, String comDtlNm) {
        return getAllCodes().stream()
                .filter(code -> comCd.equalsIgnoreCase(code.getComCd()))
                .filter(code -> comDtlNm.equals(code.getComDtlNm()))
                .map(CommonCodeVO::getComDtlCd)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "공통코드를 찾을 수 없습니다: " + comCd + "/" + comDtlNm));
    }

    /** 공통코드값을 등록된 상세코드값으로 표준화해 반환합니다. */
    public String getCodeNameByValue(String comCd, String comDtlNm) {
        return getCodeName(comCd, getDetailCode(comCd, comDtlNm));
    }

    /** 화면용 사용 중 공통코드 목록을 반환합니다. */
    public List<CommonCodeVO> getAllCodes() {
        Map<Object, Object> cachedCodes = ensureCache();
        if (cachedCodes.isEmpty()) {
            return List.of();
        }

        return cachedCodes.keySet().stream()
                .map(String::valueOf)
                .filter(field -> field.startsWith(NAME_FIELD))
                .map(field -> toCode(field.substring(NAME_FIELD.length()), cachedCodes))
                .filter(code -> code.getComDtlNm() != null)
                .sorted(Comparator
                        .comparing(CommonCodeVO::getComCdSortOrd,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CommonCodeVO::getSortOrd,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(CommonCodeVO::getComCd)
                        .thenComparing(CommonCodeVO::getComDtlCd))
                .toList();
    }

    /** DB의 사용 중 공통코드 전체를 Redis에 다시 적재합니다. */
    public List<CommonCodeVO> refreshCodes() {
        loadCodesToRedis();
        return getAllCodes();
    }

    private CommonCodeVO requireCode(String comCd, String comDtlCd) {
        Map<Object, Object> cachedCodes = ensureCache();
        String key = cacheField(comCd, comDtlCd);
        String name = valueOf(cachedCodes.get(NAME_FIELD + key));
        if (name == null) {
            throw new IllegalArgumentException(
                    "공통코드를 찾을 수 없습니다: " + comCd + "/" + comDtlCd);
        }

        CommonCodeVO code = toCode(key, cachedCodes);
        code.setComDtlNm(name);
        return code;
    }

    /** Redis 캐시가 없거나 이전 JSON 형식이면 DB 목록을 다시 적재합니다. */
    private Map<Object, Object> ensureCache() {
        Map<Object, Object> cachedCodes = redisTemplate.opsForHash().entries(CODE_KEY);
        boolean hasCurrentFormat = cachedCodes != null
                && cachedCodes.keySet().stream()
                        .map(String::valueOf)
                        .anyMatch(field -> field.startsWith(NAME_FIELD));
        if (!hasCurrentFormat) {
            loadCodesToRedis();
            cachedCodes = redisTemplate.opsForHash().entries(CODE_KEY);
        }
        return cachedCodes == null ? Map.of() : cachedCodes;
    }

    private void loadCodesToRedis() {
        List<CommonCodeVO> codes = mapper.selectActiveCodes();
        Map<String, String> cache = new LinkedHashMap<>();
        if (codes != null) {
            for (CommonCodeVO code : codes) {
                String key = cacheField(code.getComCd(), code.getComDtlCd());
                cache.put(NAME_FIELD + key, code.getComDtlNm());
                cache.put(DESCRIPTION_FIELD + key, textOf(code.getComDtlDesc()));
                cache.put(GROUP_NAME_FIELD + key, textOf(code.getComCdNm()));
                cache.put(GROUP_ORDER_FIELD + key, stringOf(code.getComCdSortOrd()));
                cache.put(DETAIL_ORDER_FIELD + key, stringOf(code.getSortOrd()));
            }
        }

        redisTemplate.delete(CODE_KEY);
        if (!cache.isEmpty()) {
            redisTemplate.opsForHash().putAll(CODE_KEY, cache);
        }
    }

    private CommonCodeVO toCode(String key, Map<Object, Object> cache) {
        CommonCodeVO code = new CommonCodeVO();
        int separator = key.indexOf(':');
        if (separator < 0) {
            throw new IllegalStateException("Redis 공통코드 필드 형식이 올바르지 않습니다: " + key);
        }

        String value = key.substring(separator + 1);
        code.setComCd(key.substring(0, separator));
        code.setComDtlCd(value);
        code.setComDtlNm(valueOf(cache.get(NAME_FIELD + key)));
        code.setComDtlDesc(valueOf(cache.get(DESCRIPTION_FIELD + key)));
        code.setComCdNm(valueOf(cache.get(GROUP_NAME_FIELD + key)));
        code.setComCdSortOrd(integerOf(cache.get(GROUP_ORDER_FIELD + key)));
        code.setSortOrd(integerOf(cache.get(DETAIL_ORDER_FIELD + key)));
        return code;
    }

    private String cacheField(String comCd, String comDtlCd) {
        return comCd.trim().toUpperCase() + ":" + comDtlCd.trim();
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String stringOf(Integer value) {
        return value == null ? "" : value.toString();
    }

    private String textOf(String value) {
        return value == null ? "" : value;
    }

    private Integer integerOf(Object value) {
        String text = valueOf(value);
        if (text == null || text.isBlank()) {
            return null;
        }
        return Integer.valueOf(text);
    }
}
