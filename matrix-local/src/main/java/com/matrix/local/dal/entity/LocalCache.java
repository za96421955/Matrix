package com.matrix.local.dal.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tbl_local_cache")
public class LocalCache {
    private Long id;
    private String cacheKey;
    private String cacheValue;
    private Long expireAt;
    private String createTime;

    public boolean isExpired() {
        if (expireAt == null || expireAt <= 0) {
            return false;
        }
        return expireAt < (System.currentTimeMillis() / 1000);
    }
}
