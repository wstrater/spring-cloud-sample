package com.example.springcloudsample;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@SpringBootApplication
public class SpringCloudSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudSampleApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        String uri = "http://httpbin.org:80";

        RouteLocator ret = builder.routes()
                .route("get_route", r -> r.path("/get")
                        .uri("http://httpbin.org"))
                .route("hystrix_route", r -> r.host("*.hystrix.org")
                        .filters(filter -> filter.hystrix(c -> c.setName("slow-command")))
                        .uri(uri))
                .route("fallback_route", r -> r.host("*.fallback.org")
                        .filters(filter -> filter.hystrix(c -> c.setName("slow-command").setFallbackUri("forward:/fallback")))
                        .uri(uri))
                .build();

        ret.getRoutes().subscribe(route -> {
            log.info("Route: {} -> ({}) {}", route.getId(), route.getOrder(), route.getUri());
            log.info("    Predicate: {}", route.getPredicate());
            route.getFilters()
                    .forEach(filter -> log.info("    Filter: {}", filter.getClass().getName()));
        });

        return ret;
    }

    @GetMapping("/fallback")
    String hystrixFallback() {
        log.warn("Falling back");
        return "Unable to proxy request";
    }

}