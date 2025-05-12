package com.example.webfluxflow.service;

import com.example.webfluxflow.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static java.nio.charset.StandardCharsets.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserQueueService {

    private static final String USER_QUEUE_WAIT_KEY_PREFIX = "users:queue:";
    private static final String USER_QUEUE_WAIT_KEY_SUFFIX = ":wait";
    private static final String USER_QUEUE_PROCEED_KEY_SUFFIX = ":proceed";
    private final String USER_QUEUE_WAIT_KEY_FOR_SCAN = "users:queue:*:wait";

    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    // 대기열 등록 API
    public Mono<Long> registerWaitQueue(final String queue, final Long userId) {
        /**
         * redis sortedSet
         * key: userId
         * value: unix timestamp
         */
        long unixTimestamp = Instant.now().getEpochSecond();
        String waitQueueKey = USER_QUEUE_WAIT_KEY_PREFIX + queue + USER_QUEUE_WAIT_KEY_SUFFIX;

        return reactiveRedisTemplate.opsForZSet().add(waitQueueKey, userId.toString(), unixTimestamp)
                .filter(i -> i) // true or false 중 true 값일 때만 flatMap 동작, false이면 아무것도 넘겨주지 않으므로 exception 발생
                .switchIfEmpty(Mono.error(ErrorCode.QUEUE_ALREADY_REGISTERED_USER.build()))
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank(waitQueueKey, userId.toString()))
                .map(i -> i >= 0 ? i + 1 : i);
    }

    // 진입을 허용
    public Mono<Long> allowUser(final String queue, final Long count) {
        // 진입을 허용하는 단계
        // 1. wait queue 사용자를 제거
        // 2. proceed queue 사용자를 추가
        String waitQueueKey = USER_QUEUE_WAIT_KEY_PREFIX + queue + USER_QUEUE_WAIT_KEY_SUFFIX;
        String proceedQueueKey = USER_QUEUE_WAIT_KEY_PREFIX + queue + USER_QUEUE_PROCEED_KEY_SUFFIX;


        return reactiveRedisTemplate.opsForZSet().popMin(waitQueueKey, count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet().add(proceedQueueKey, member.getValue(), Instant.now().getEpochSecond()))
                .count();
    }

    // 진입이 가능한 상태?
    public Mono<Boolean> isAllowed(final String queue, final Long userId) {
        String proceedQueueKey = USER_QUEUE_WAIT_KEY_PREFIX + queue + USER_QUEUE_PROCEED_KEY_SUFFIX;

        return reactiveRedisTemplate.opsForZSet().rank(proceedQueueKey, userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0);
    }

    // 사용자의 대기번호(주기적으로 체크해야함)
    public Mono<Long> getRank(final String queue, final Long userId) {
        String waitQueueKey = USER_QUEUE_WAIT_KEY_PREFIX + queue + USER_QUEUE_WAIT_KEY_SUFFIX;

        return reactiveRedisTemplate.opsForZSet().rank(waitQueueKey, userId.toString())
                .defaultIfEmpty(-1L)
                .map(rank -> rank >= 0 ? rank + 1 : rank);
    }

    // 3초 단위로 proceed queue에 사용자 등록 (모든 waiting queue의 키값과 일치하는 100개의 queue에 있는 사용자들을 3명씩 스케쥴링)
    @Scheduled(initialDelay = 10000, fixedDelay = 10000)
    public void scheduleAllowUser() {
        log.info("Scheduled allow user queue");
        Long maxAllowUserCount = 3L;

        reactiveRedisTemplate.
                scan(ScanOptions.scanOptions()
                        .match(USER_QUEUE_WAIT_KEY_FOR_SCAN)
                        .count(100)
                        .build())
                .doOnNext(key -> log.info("Scanned Key: {}", key))
                .map(key -> key.split(":")[2])
                .flatMap(queue -> allowUser(queue, maxAllowUserCount).map(allowed -> Tuples.of(queue, allowed)))
                .doOnNext(tuple -> log.info("Tried %d and allowed %d members of %s queue".formatted(maxAllowUserCount, tuple.getT2(), tuple.getT1())))
                .subscribe();
    }

    // 웹페이지 변환되는 시점에 검증 Token
    public Mono<String> generateToken(final String queue, final Long userId) {
        // sha256 값 생성
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            var input = "user-queue-%s-%d".formatted(queue, userId);
            byte[] encodedHash = digest.digest(input.getBytes(UTF_8));
            // byte 데이터를 그대로 쓸 수 없기 때문에 HEX String 으로 변환
            StringBuilder hexString = new StringBuilder();
            for(byte b : encodedHash) {
                hexString.append(String.format("%02x", b));
            }
            return Mono.just(hexString.toString());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // 토큰 검증 로직 추가 (사용자 Token & 요청 Token 비교)
    public Mono<Boolean> isAllowedByToken(final String queue, final Long userId, final String token){
        return this.generateToken(queue, userId)
                .filter(gen -> gen.equalsIgnoreCase(token))
                .map(i -> true)
                .defaultIfEmpty(false);
    }

}
