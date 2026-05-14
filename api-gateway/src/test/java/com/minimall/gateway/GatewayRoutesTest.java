package com.minimall.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "USER_SERVICE_BASE_URL=http://user-service.test",
        "PRODUCT_SERVICE_BASE_URL=http://product-service.test",
        "INVENTORY_SERVICE_BASE_URL=http://inventory-service.test",
        "ORDER_SERVICE_BASE_URL=http://order-service.test",
        "PAYMENT_SERVICE_BASE_URL=http://payment-service.test"
})
class GatewayRoutesTest {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void configuresFrontendServiceRoutesWithEnvironmentDrivenUris() {
        Map<String, RouteDefinition> routes = routesById();

        assertRoute(routes, "user-service", "http://user-service.test", "/api/user/**");
        assertRoute(routes, "product-service", "http://product-service.test", "/api/product/**");
        assertRoute(routes, "inventory-service", "http://inventory-service.test", "/api/inventory/**");
        assertRoute(routes, "order-service", "http://order-service.test", "/api/order/**");
        assertRoute(routes, "payment-service", "http://payment-service.test", "/api/payment/**");
    }

    @Test
    void routeFiltersRewriteFrontendPrefixesToDownstreamApiPaths() {
        Map<String, RouteDefinition> routes = routesById();

        assertRewrite(routes, "user-service", "/api/user/(?<segment>.*)", "/api/$\\{segment}");
        assertRewrite(routes, "product-service", "/api/product/(?<segment>.*)", "/api/$\\{segment}");
        assertRewrite(routes, "inventory-service", "/api/inventory/(?<segment>.*)", "/api/$\\{segment}");
        assertRewrite(routes, "order-service", "/api/order/(?<segment>.*)", "/api/$\\{segment}");
        assertRewrite(routes, "payment-service", "/api/payment/(?<segment>.*)", "/api/$\\{segment}");
    }

    private Map<String, RouteDefinition> routesById() {
        return gatewayProperties.getRoutes().stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
    }

    private void assertRoute(
            Map<String, RouteDefinition> routes,
            String routeId,
            String uri,
            String pathPattern) {
        RouteDefinition route = routes.get(routeId);
        assertThat(route).as("route " + routeId).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create(uri));
        assertThat(pathPredicateArgs(route)).contains(pathPattern);
    }

    private void assertRewrite(
            Map<String, RouteDefinition> routes,
            String routeId,
            String regexp,
            String replacement) {
        RouteDefinition route = routes.get(routeId);
        assertThat(route).as("route " + routeId).isNotNull();
        List<String> rewriteArgs = route.getFilters().stream()
                .filter(filter -> "RewritePath".equals(filter.getName()))
                .flatMap(filter -> filter.getArgs().values().stream())
                .toList();

        assertThat(rewriteArgs).contains(regexp, replacement);
    }

    private List<String> pathPredicateArgs(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(args -> args.values().stream())
                .toList();
    }
}
