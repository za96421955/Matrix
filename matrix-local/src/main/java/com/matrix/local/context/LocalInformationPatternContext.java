package com.matrix.local.context;

import com.matrix.common.enums.InformationPattern;
import com.matrix.common.enums.RedisKey;
import com.matrix.local.service.LocalCacheService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * @description 资料上下文（本地版）
 * <p> 替代 Redis 实现，使用 LocalCacheService (tbl_local_cache) 持久化 </p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalInformationPatternContext {

    @Resource
    private LocalCacheService localCacheService;

    /**
     * @description 清除当前环节
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void clear(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.INFORMATION_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        localCacheService.delete(key);
        log.debug("[资料模式] userId={}, sessionId={}, 清除环节", userId, sessionId);
    }

    /**
     * @description 设置当前环节
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void setPatternNo(long userId, long sessionId, int no) {
        RedisKey redisKey = RedisKey.INFORMATION_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        localCacheService.put(key, String.valueOf(no), redisKey.getTtl().intValue());
        log.debug("[资料模式] userId={}, sessionId={}, 设置环节={}", userId, sessionId, no);
    }

    /**
     * @description 获取当前环节
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public int getPatternNo(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.INFORMATION_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        String no = localCacheService.get(key);
        if (StringUtils.isBlank(no)) {
            return InformationPattern.NONE.getNo();
        }
        return Integer.parseInt(no);
    }

    /**
     * @description 下一环节
     * <p> <功能详细描述> </p>
     *
     * @author 陈晨
     */
    public void next(long userId, long sessionId, InformationPattern next) {
        if (null == next) {
            return;
        }
        this.setPatternNo(userId, sessionId, next.getNo());
        log.info("[资料模式] userId={}, sessionId={}, next={}, 设置下一环节", userId, sessionId, next.getNo());
    }

}
