package com.gym.gateway.config;

import com.gym.gateway.filter.AggregationFilter;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Configuration
public class GatewayConfig {

    @Bean
    public GlobalFilter customGlobalFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                return chain.filter(exchange.mutate()
                        .request(request.mutate()
                                .header(HttpHeaders.AUTHORIZATION, authHeader)
                                .build())
                        .build());
            }
            return chain.filter(exchange);
        };
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder, AggregationFilter aggregationFilter) {
        return builder.routes()
                .route("trainers-service", r -> r.path("/trainers/**")
                        .uri("lb://trainers-service"))
                .route("classes-service", r -> r.path("/classes/**")
                        .uri("lb://classes-service"))
                .route("members-service", r -> r.path("/members/**")
                        .uri("lb://members-service"))
                .route("equipment-service", r -> r.path("/equipment/**")
                        .uri("lb://equipment-service"))
                .route("aggregated-info", r -> r.path("/aggregated-info")
                        .filters(f -> f.filter(aggregationFilter))
                        .uri("no://op"))
                .build();

    }
}
