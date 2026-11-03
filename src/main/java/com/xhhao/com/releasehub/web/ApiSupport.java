package com.xhhao.com.releasehub.web;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import java.util.Map;
import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.http.*;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;
import io.swagger.v3.oas.annotations.enums.ParameterIn;

public final class ApiSupport {
    private ApiSupport() { }
    public record Result(boolean success, String message) { }
    public record TokenInput(String token) { }
    public record StateInput(String state) { }
    public static Mono<ServerResponse> ok(Object value) { return ServerResponse.ok().header("Cache-Control", "no-store").contentType(MediaType.APPLICATION_JSON).bodyValue(value); }
    public static <T> Mono<T> body(ServerRequest request, Class<T> type) {
        if (request.headers().contentLength().orElse(0) > 65536)
            return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));
        return request.bodyToMono(type).switchIfEmpty(Mono.error(new IllegalArgumentException("请求内容不能为空")));
    }
    public static Mono<ServerResponse> done(String message) { return ok(new Result(true, message)); }
    public static int integer(ServerRequest r, String name, int fallback) {
        try { return Integer.parseInt(r.queryParam(name).orElse(String.valueOf(fallback))); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("分页参数不正确"); }
    }
    public static void operation(Builder b, String id, String tag, Class<?> response, Class<?> input, String... paths) {
        b.operationId(id).tag(tag).response(responseBuilder().responseCode("200").implementation(response));
        if (input != null) b.requestBody(requestBodyBuilder().required(true).implementation(input));
        for (String path : paths) b.parameter(parameterBuilder().name(path).in(ParameterIn.PATH).required(true).implementation(String.class));
    }
    public static void query(Builder b, String name, Class<?> type) { b.parameter(parameterBuilder().name(name).in(ParameterIn.QUERY).implementation(type)); }
    public static Mono<ServerResponse> failure(Throwable error, ServerRequest r) {
        HttpStatusCode status = error instanceof org.springframework.web.server.ResponseStatusException e ? e.getStatusCode()
            : error instanceof org.springframework.dao.OptimisticLockingFailureException ? HttpStatus.CONFLICT
            : error instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        String message = error instanceof IllegalArgumentException ? error.getMessage() : status.value() == 429
            ? "请求过于频繁，请稍后再试" : status.value() == 404 ? "内容不存在" : "操作失败，请检查配置或稍后重试";
        return ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("status", status.value(), "message", message));
    }
}
