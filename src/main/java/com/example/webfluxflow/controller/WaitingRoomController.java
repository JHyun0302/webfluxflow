package com.example.webfluxflow.controller;

import com.example.webfluxflow.service.UserQueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.result.view.Rendering;
import reactor.core.publisher.Mono;

@Controller
@RequiredArgsConstructor
public class WaitingRoomController {

    private final UserQueueService userQueueService;

    /**
     * test url: http://localhost:9010/waiting-room?user_id=1&redirect_url=https://www.naver.com
     */
    @GetMapping("/waiting-room")
    Mono<Rendering> waitingRoomPage(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                    @RequestParam(name = "user_id") Long userId,
                                    @RequestParam(name = "redirect_url") String redirectUrl){
        // 대기 등록
        // 웹페이지 필요한 데이터를 전달
        return userQueueService.isAllowed(queue, userId)
                .filter(allowed -> allowed)
                .flatMap(allowed -> Mono.just(Rendering.redirectTo(redirectUrl).build()))
                .switchIfEmpty(userQueueService.registerWaitQueue(queue, userId)
                        .onErrorResume(ex -> userQueueService.getRank(queue, userId))
                        .map(rank -> Rendering.view("waiting-room.html")
                                .modelAttribute("number", rank)
                                .modelAttribute("userId", userId)
                                .modelAttribute("queue", queue)
                                .build()));
    }

    // 사용자 접속 대기 시스템: 1000명 초과 시 대기 처리
    @GetMapping("/map")
    public Mono<Rendering> mapPage(@RequestParam(name = "queue", defaultValue = "default") String queue,
                                   @RequestParam(name = "user_id") Long userId,
                                   @RequestParam(name = "redirect_url") String redirectUrl) {
        return userQueueService.canEnterProceedQueue(queue)
                .flatMap(canEnter -> {
                    if (canEnter) {
                        // 1000명 이하 - 즉시 접속 (진행열로 이동)
                        return userQueueService.registerProceedQueue(queue, userId)
                                .then(Mono.just(Rendering.redirectTo(redirectUrl).build()));
                    } else {
                        // 1000명 초과 - 대기열로 이동
                        return userQueueService.registerWaitQueue(queue, userId)
                                .map(rank -> Rendering.view("waiting-room.html")
                                        .modelAttribute("number", rank)
                                        .modelAttribute("userId", userId)
                                        .modelAttribute("queue", queue)
                                        .build());
                    }
                });
    }
}
