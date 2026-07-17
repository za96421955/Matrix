package com.matrix.local.context;

import com.matrix.common.enums.CodingPattern;
import com.matrix.common.enums.RedisKey;
import com.matrix.local.service.LocalCacheService;
import com.matrix.service.context.CodingPatternContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * @description 编程上下文 (local 实现)
 * <p> 使用 LocalCacheService 替代 RedisTemplate，所有持久化改为 SQLite tbl_local_cache </p>
 *
 * @author 陈晨
 */
@Slf4j
@Primary
@Component
public class LocalCodingPatternContext extends CodingPatternContext {

    @Resource
    private LocalCacheService localCacheService;

    public void clear(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.CODING_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        localCacheService.delete(key);
    }

    /**
     * @description 设置当前环节
     */
    public void setPatternNo(long userId, long sessionId, int no) {
        RedisKey redisKey = RedisKey.CODING_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        localCacheService.put(key, no + "", redisKey.getTtl());
    }

    /**
     * @description 获取当前环节
     */
    public int getPatternNo(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.CODING_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        String no = localCacheService.get(key);
        return StringUtils.isBlank(no) ? CodingPattern.NONE.getNo() : Integer.parseInt(no);
    }

    /**
     * @description 下一环节
     */
    public void next(long userId, long sessionId, CodingPattern next) {
        if (null == next) {
            return;
        }
        this.setPatternNo(userId, sessionId, next.getNo());
        log.info("[编程模式] userId={}, next={}, 设置下一环节",
                userId, next.getNo());
    }

}
