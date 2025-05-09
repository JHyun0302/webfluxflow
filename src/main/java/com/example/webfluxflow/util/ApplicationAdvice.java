package com.example.webfluxflow.util;

import com.example.webfluxflow.exception.ApplicationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

@RestControllerAdvice // 예외처리 할 클래스 설정 & return값은 Json 형식
public class ApplicationAdvice {

    @ExceptionHandler(ApplicationException.class)
    public Mono<ResponseEntity<ServerExceptionResponse>> handleApplicationException(ApplicationException ex) {
        return Mono.just(ResponseEntity
                .status(ex.getStatus())
                .body(new ServerExceptionResponse(ex.getCode(), ex.getReason())));

    }

    public record ServerExceptionResponse(String code, String reason){

    }
}
