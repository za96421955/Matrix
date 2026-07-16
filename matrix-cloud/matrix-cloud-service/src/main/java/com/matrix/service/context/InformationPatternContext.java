package com.matrix.service.context;

import com.matrix.common.enums.InformationPattern;
import com.matrix.common.enums.RedisKey;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @description 资料上下文
 * <p> <功能详细描述> </p>
 *
 * @author 陈晨
 */
@Slf4j
@Component
public class InformationPatternContext {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    public void clear(long userId, long sessionId) {
        RedisKey redisKey = RedisKey.INFORMATION_PATTERN_NO;
        String key = redisKey.generateKey(userId, sessionId);
        redisTemplate.delete(key);
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
        redisTemplate.opsForValue().set(key, no + "", redisKey.getTtl(), TimeUnit.SECONDS);
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
        String no = (String) redisTemplate.opsForValue().get(key);
        return StringUtils.isBlank(no) ? InformationPattern.NONE.getNo() : Integer.parseInt(no);
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
        log.info("[编程模式] userId={}, next={}, 设置下一环节",
                userId, next.getNo());
    }

}


