package com.matrix.service.dal.sharding;

import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingAlgorithm;
import org.apache.shardingsphere.sharding.api.sharding.complex.ComplexKeysShardingValue;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 取模分片算法实现类
 */
@Slf4j
public class HashAlgorithm implements ComplexKeysShardingAlgorithm<Long> {

    @Override
    /** doSharding操作 */
    public Collection<String> doSharding(Collection<String> availableTargetNames, ComplexKeysShardingValue<Long> shardingValue) {
        Map<String, Collection<Long>> columnShardingValues = shardingValue.getColumnNameAndShardingValuesMap();
        Set<String> tables = new HashSet<>();
        columnShardingValues.forEach((column, values) -> {
            // null 或没有值，返回所有分表
            if (CollectionUtils.isEmpty(values)) {
                log.info("sharding column {} value is null or empty, returning all shards: {}",
                        column, availableTargetNames);
                tables.addAll(availableTargetNames);
            }
            // 路由到多个分表
            tables.addAll(getShardingTables(availableTargetNames, values));
        });
        return tables;
    }

    private Set<String> getShardingTables(Collection<String> availableTargetNames, Collection<Long> values) {
        Set<String> result = new HashSet<>();
        for (Long value : values) {
            int index = (int) (value % availableTargetNames.size());
            String targetSuffix = String.format("_%04d", index);
            for (String targetName : availableTargetNames) {
                if (targetName.endsWith(targetSuffix)) {
                    result.add(targetName);
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("tables[" + availableTargetNames + "], values[" + values + "], 没有匹配的分表");
        }
        return result;
    }

}


