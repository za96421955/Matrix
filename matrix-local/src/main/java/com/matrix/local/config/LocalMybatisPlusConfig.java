package com.matrix.local.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Local MyBatis Plus 配置
 * 分页插件使用 SQLite 方言，保留乐观锁插件
 * Mapper 扫描路径复用 service 模块的 dal mapper 包
 */
@Configuration
@MapperScan("com.matrix.service.dal.mapper")
public class LocalMybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 分页插件 - SQLite 方言
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
        // 2. 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

}
