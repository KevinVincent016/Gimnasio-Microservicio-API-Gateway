package com.gym.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Component
public class AggregationFilter implements GatewayFilter {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getPath().value().equals("/aggregated-info")) {
            return aggregateResponses(exchange);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> aggregateResponses(ServerWebExchange exchange) {
        Mono<String> catalogoInfo = webClientBuilder.build().get()
                .uri("http://catalogo-service/catalogo/info")
                .retrieve()
                .bodyToMono(String.class);
        Mono<String> circulacionInfo = webClientBuilder.build().get()
                .uri("http://circulacion-service/circulacion/info")
                .retrieve()
                .bodyToMono(String.class);
        return Mono.zip(catalogoInfo, circulacionInfo, (catalogo, circulacion) -> {
                    Map<String, String> result = new HashMap<>();
                    result.put("catalogo", catalogo);
                    result.put("circulacion", circulacion);
                    return result;
                })
                .flatMap(result -> {
                    exchange.getResponse().setStatusCode(HttpStatus.OK);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    try {
                        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                                .bufferFactory().wrap(new ObjectMapper().writeValueAsBytes(result))));
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException(e));
                    }
                });
    }
}



