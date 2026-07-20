//package com.matrix.local.config;
//
//import io.lettuce.core.RedisClient;
//import io.lettuce.core.api.StatefulRedisConnection;
//import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//
//@Configuration
//public class LocalRedisConfig {
//
//    @Bean
//    @ConditionalOnMissingBean(RedisConnectionFactory.class)
//    public RedisConnectionFactory redisConnectionFactory() {
//        LettuceConnectionFactory factory = new LettuceConnectionFactory();
//        factory.setValidateConnection(false);
//        factory.setShareNativeConnection(false);
//        return factory;
//    }
//}
