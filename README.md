# Issue with Spring Cloud Gateway and Hystrix Filter

I am trying to build a Spring Cloud Gateway server and started failing 
when I added a `Hystrix` filter to a `RouteLocatorBuilder`.

I am new to Spring WebFlux, Spring Cloud Gateway and the Reactor project 
so the issue may be somewhere between seat and keyboard but the following 
are the steps I took to recreate the error.

I submitted issue 
[633](https://github.com/spring-cloud/spring-cloud-gateway/issues/633)
on Git Hub 
[spring-cloud/spring-cloud-gateway](https://github.com/spring-cloud/spring-cloud-gateway).

## Background

I used [Spring Intializr](https://start.spring.io/) to create 
a Gradle project using Spring Boot Version `2.1.0.RELEASE`.
I added the following dependencies.

* Actuator
* Reactive Web
* Gateway
* Ribbon
* Hystrix
* Lombok

## RouteLocator Bean that Works

Here is the basic `RouteLocator` slimmed down that works.
I copied this from someone's tutorial.
The version checked in has some logging.

Git commit [3d3ebbb7edc1e8c0cb8da3ff35dc77f09a474cbc](https://github.com/wstrater/spring-cloud-sample/commit/3d3ebbb7edc1e8c0cb8da3ff35dc77f09a474cbc).

```
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("path_route", r -> r.path("/get")
                        .uri("http://httpbin.org"))
                .route("host_route", r -> r.host("*.myhost.org")
                        .uri(uri))
                .build();
    }
```

Listing the gateway routes works.

```
curl http://localhost:8080/manage/gateway/routes
[
  {
    "route_id": "path_route",
    "route_object": {
      "predicate": "org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$279/79620878@7275c74b"
    },
    "order": 0
  },
  {
    "route_id": "host_route",
    "route_object": {
      "predicate": "org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$279/79620878@42210be1"
    },
    "order": 0
  }
]
```

The `curl` output.

```
curl http://localhost:8080/get
{
  "args": {},
  "headers": {
    "Accept": "*/*",
    "Connection": "close",
    "Forwarded": "proto=http;host=\"localhost:8080\";for=\"127.0.0.1:54458\"",
    "Host": "httpbin.org",
    "User-Agent": "curl/7.49.0",
    "X-Forwarded-Host": "localhost:8080"
  },
  "origin": "127.0.0.1, 24.63.75.15",
  "url": "http://localhost:8080/get"
}
```

Same request but just showing the HTTP Status.

```
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/get
200
```

## RouteLocator Bean that Fails

Here is the `RouteLocator` with the added `filter`.

Git commit [9c4b16d9003804bc74ae490f77f509ab37d6e61e](https://github.com/wstrater/spring-cloud-sample/commit/9c4b16d9003804bc74ae490f77f509ab37d6e61e).

```
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("path_route", r -> r.path("/get")
                        .uri("http://httpbin.org"))
                .route("host_route", r -> r.host("*.myhost.org")
                        .filters(filter -> filter.hystrix(c -> c.setName("slow-command")))
                        .uri(uri))
                .build();
    }
```

The `RouteLocator` bean is successfully generated and the logged output
looks reasonable and looks similar to the output when it works
plus the filter.

```
Route: path_route -> (0) http://httpbin.org:80
    Predicate: org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$279/256139608@3791f50e
Route: host_route -> (0) http://httpbin.org:80
    Predicate: org.springframework.cloud.gateway.support.ServerWebExchangeUtils$$Lambda$279/256139608@44ed0a8f
    Filter: org.springframework.cloud.gateway.filter.OrderedGatewayFilter
```

Not sure I am using the best approach for logging but it appears to
work and I see no errors in the console output.

```
        ret.getRoutes().subscribe(route -> {
            log.info("Route: {} -> ({}) {}", route.getId(), route.getOrder(), route.getUri());
            log.info("    Predicate: {}", route.getPredicate());
            route.getFilters()
                    .forEach(filter -> log.info("    Filter: {}", filter.getClass().getName()));
        });
```

I only become aware that things are not working when I start using `curl`.
You can see the routes are missing.

```
curl http://localhost:8080/manage/gateway/routes
{
  "timestamp": "2018-11-03T14:39:24.612+0000",
  "path": "/manage/gateway/routes",
  "status": 404,
  "error": "Not Found",
  "message": null
}
```

As expected the request to the gateway fails too.

```
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/get
404
```

## Differences I Found

As I said I am new to Spring Cloud Gateway, WebFlux and Reactor but 
did spend time trying to find out what was wrong.
Never really found it but did find a difference that might help others.

If you put a break point in `org.springframework.web.reactive.DispatcherHandler#handle`
and inspect `org.springframework.web.reactive.DispatcherHandler#handlerMappings`
you can see the difference.

| Without Filter                   | With Filter                  |
| :---                             | :---                         |
| WebFluxEndpointHandlerMapping    | RouterFunctionMapping        |
| ControllerEndpointHandlerMapping | RequestMappingHandlerMapping |
| RouterFunctionMapping            | SimpleUrlHandlerMapping      |
| RequestMappingHandlerMapping     |                              |
| RoutePredicateHandlerMapping     |                              |
| SimpleUrlHandlerMapping          |                              |

## Solution

Changing `springCloudVersion` from `Greenwich.M1` to `Greenwich.BUILD-SNAPSHOT` 
solved the issue but needed to add ["https://repo.spring.io/snapshot]("https://repo.spring.io/snapshot)
to the list of repositories. 

```
repositories {
	mavenCentral()
	maven { url "https://repo.spring.io/milestone" }
	maven { url "https://repo.spring.io/snapshot" }
}

ext {
	springCloudVersion = 'Greenwich.BUILD-SNAPSHOT'
}
```

## Other Items

The remaining are some other items I noticed on my 
journey.
Not really worth noting on their own but included them
here.

### Known Bug

While working with the gateway if kept getting an exception in
`org.springframework.cloud.gateway.filter.NettyRoutingFilter`
This appears to be a known bug and a fix is scheduled to be 
released.
I copied the code from the post to work around it for now.

### RouteLocatorBuilder Compile Error

I am using IntelliJ 2018.2.5 and get the following compile error displayed
while coding. 

> Could not autowire. No beans of 'RouteLocatorBuilder' type found.

The code does compile so it does not stop me from working but may
worth looking into or writing off as developer error.

### Route Locator Sample

I tried using the code from 
[Spring Cloud Gateway 2.1.0 M1](https://spring.io/projects/spring-cloud-gateway)
but could not locate the `Routes` class.
The issue may be that I am using `2.1.0.RELEASE` instead of `M1`.
Tried switching but could locate the `M1` respository.

```
  @Bean
  public RouteLocator customRouteLocator(ThrottleWebFilterFactory throttle) {
    return Routes.locator()
      .route("test")
          .uri("http://httpbin.org:80")
          .predicate(host("**.abc.org").and(path("/image/png")))
          .addResponseHeader("X-TestHeader", "foobar")
          .and()
      .route("test2")
          .uri("http://httpbin.org:80")
          .predicate(path("/image/webp"))
          .add(addResponseHeader("X-AnotherHeader", "baz"))
          .and()
      .build();
  }
```

I found a similar example in 
[Fluent Java Routes API](http://cloud.spring.io/spring-cloud-static/spring-cloud-gateway/2.0.0.M4/multi/multi__configuration.html).
Not sure if this my issue or changes to the code.

