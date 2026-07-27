package com.matrix.local.primary;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.matrix.local.dal.DateToLongTypeHandler;
import jakarta.annotation.Resource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;

/**
 * Local MyBatis Plus 配置
 * 分页插件使用 SQLite 方言，保留乐观锁插件
 * Mapper 扫描路径复用 service 模块的 dal mapper 包
 * <p>
 * 注册 DateToLongTypeHandler，将所有 java.util.Date 映射为 INTEGER (Unix 毫秒时间戳)，
 * 避免 SQLite TEXT 列存时间戳时的格式解析异常。
 */
@Configuration
@MapperScan("com.matrix.local.dal.mapper")
@Primary
public class LocalMybatisPlusConfig {

    @Resource
    private DataSource dataSource;

    @Value("${mybatis-plus.mapper-locations[0]}")
    private String location;

    @Bean
    /** sqlSessionFactory操作 */
    public SqlSessionFactory sqlSessionFactory() throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources(location));
        // 注册自定义 TypeHandler：Date <-> Long
        factoryBean.setTypeHandlers(new DateToLongTypeHandler());
        // 注册 MyBatis Plus 插件
        factoryBean.setPlugins(this.localMybatisPlusInterceptor());
        return factoryBean.getObject();
    }

    @Bean
    /** localMybatisPlusInterceptor操作 */
    public MybatisPlusInterceptor localMybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 分页插件 - SQLite 方言
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.SQLITE));
        // 2. 乐观锁插件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

}
