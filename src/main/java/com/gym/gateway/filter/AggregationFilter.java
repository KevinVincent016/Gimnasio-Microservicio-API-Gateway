package com.gym.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.gateway.dto.ResumenDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class AggregationFilter implements GatewayFilter {

    @Autowired
    private WebClient.Builder webClientBuilder;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        if (path.startsWith("/resumen-miembro/")) {
            return aggregateMemberInfo(exchange);
        }
        
        if (path.equals("/aggregated-info")) {
            return aggregateResponses(exchange);
        }
        
        return chain.filter(exchange);
    }

    private Mono<Void> aggregateMemberInfo(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        String memberId = path.substring("/resumen-miembro/".length());
        
        // Extract JWT token from Authorization header
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        
        // Create WebClient with JWT token
        WebClient webClient = webClientBuilder.build();
        
        // Call members service
        Mono<Object> miembroInfo = webClient.get()
                .uri("http://localhost:8083/api/members/" + memberId)
                .header(HttpHeaders.AUTHORIZATION, authHeader != null ? authHeader : "")
                .retrieve()
                .bodyToMono(Object.class)
                .onErrorReturn(createErrorResponse("miembros", "Servicio no disponible"));

        // Call classes service
        Mono<List<Object>> clasesInfo = webClient.get()
                .uri("http://localhost:8082/api/classes?miembroId=" + memberId)
                .header(HttpHeaders.AUTHORIZATION, authHeader != null ? authHeader : "")
                .retrieve()
                .bodyToMono(Object.class)
                .map(response -> {
                    if (response instanceof List) {
                        return (List<Object>) response;
                    }
                    return List.of(response);
                })
                .onErrorReturn(List.of(createErrorResponse("clases", "Servicio no disponible")));

        // Derive entrenadorIds from clases and fetch entrenadores
        Mono<List<Object>> entrenadoresInfo = clasesInfo.flatMap(clases ->
                Flux.fromIterable(extractEntrenadorIds(clases))
                        .flatMap(entrenadorId -> webClient.get()
                                .uri("http://localhost:8081/api/trainers/" + entrenadorId)
                                .header(HttpHeaders.AUTHORIZATION, authHeader != null ? authHeader : "")
                                .retrieve()
                                .bodyToMono(Object.class)
                                .onErrorReturn(createErrorResponse("entrenadores", "Servicio no disponible"))
                        )
                        .collectList()
        );

        // Fetch equipos list (all equipment)
        Mono<List<Object>> equiposInfo = webClient.get()
                .uri("http://localhost:8084/api/equipment")
                .header(HttpHeaders.AUTHORIZATION, authHeader != null ? authHeader : "")
                .retrieve()
                .bodyToMono(Object.class)
                .map(response -> {
                    if (response instanceof List) {
                        return (List<Object>) response;
                    }
                    return List.of(response);
                })
                .onErrorReturn(List.of(createErrorResponse("equipos", "Servicio no disponible")));

        // Combine all responses
        return Mono.zip(miembroInfo, clasesInfo, entrenadoresInfo, equiposInfo)
                .map(tuple -> {
                    ResumenDTO resumen = new ResumenDTO();
                    resumen.setMiembro(tuple.getT1());
                    resumen.setClases(tuple.getT2());
                    resumen.setEntrenadores(tuple.getT3());
                    resumen.setEquipos(tuple.getT4());
                    return resumen;
                })
                .flatMap(resumen -> {
                    exchange.getResponse().setStatusCode(HttpStatus.OK);
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse()
                                .bufferFactory().wrap(mapper.writeValueAsBytes(resumen))));
                    } catch (JsonProcessingException e) {
                        return Mono.error(new RuntimeException("Error serializing response", e));
                    }
                });
    }

    private Map<String, String> createErrorResponse(String servicio, String mensaje) {
        return Map.of("error", mensaje, "servicio", servicio);
    }

    private Set<String> extractEntrenadorIds(List<Object> clases) {
        Set<String> ids = new LinkedHashSet<>();
        if (clases == null) {
            return ids;
        }
        for (Object clase : clases) {
            if (clase instanceof Map) {
                Object entrenadorId = ((Map<?, ?>) clase).get("entrenadorId");
                if (entrenadorId instanceof Number) {
                    ids.add(String.valueOf(((Number) entrenadorId).longValue()));
                } else if (entrenadorId != null) {
                    ids.add(String.valueOf(entrenadorId));
                }
            }
        }
        return ids;
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
                    Map<String, String> result = new java.util.HashMap<>();
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



