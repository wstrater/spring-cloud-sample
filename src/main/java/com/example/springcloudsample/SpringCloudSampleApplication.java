package com.example.springcloudsample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpringCloudSampleApplication {

    final Logger log = LoggerFactory.getLogger(getClass());

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudSampleApplication.class, args);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        String uri = "http://httpbin.org:80";

        RouteLocator ret = builder.routes()
                .route("path_route", r -> r.path("/get")
                        .uri("http://httpbin.org"))
                .route("host_route", r -> r.host("*.myhost.org")
                        .filters(filter -> filter.hystrix(c -> c.setName("slow-command")))
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

}