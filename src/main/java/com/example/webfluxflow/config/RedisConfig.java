//package com.example.webfluxflow.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
//import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.ReactiveRedisTemplate;
//import org.springframework.data.redis.core.ReactiveValueOperations;
//import org.springframework.data.redis.serializer.RedisSerializationContext;
//import org.springframework.data.redis.serializer.StringRedisSerializer;
//
//@Configuration
//public class RedisConfig {
//
//    @Value("${spring.data.redis.host:localhost}")
//    private String redisHost;
//
//    @Value("${spring.data.redis.port:6379}")
//    private int redisPort;
//
//    @Bean
//    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
//        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
//        config.setHostName(redisHost);
//        config.setPort(redisPort);
//        return new LettuceConnectionFactory(config);
//    }
//
//    @Bean
//    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
//        return new ReactiveRedisTemplate<>(factory, redisSerializationContext());
//    }
//
//    private RedisSerializationContext<String, String> redisSerializationContext() {
//        return RedisSerializationContext.<String, String>newSerializationContext(new StringRedisSerializer())
//                .key(new StringRedisSerializer())
//                .value(new StringRedisSerializer())
//                .hashKey(new StringRedisSerializer())
//                .hashValue(new StringRedisSerializer())
//                .build();
//    }
//
//    @Bean
//    public ReactiveValueOperations<String, String> reactiveValueOperations(ReactiveRedisTemplate<String, String> reactiveRedisTemplate) {
//        return reactiveRedisTemplate.opsForValue();
//    }
//}
