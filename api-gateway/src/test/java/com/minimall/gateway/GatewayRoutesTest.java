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
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "USER_SERVICE_BASE_URL=http://user-service.test",
        "PRODUCT_SERVICE_BASE_URL=http://product-service.test",
        "INVENTORY_SERVICE_BASE_URL=http://inventory-service.test",
        "ORDER_SERVICE_BASE_URL=http://order-service.test",
        "PAYMENT_SERVICE_BASE_URL=http://payment-service.test",
        "NOTIFICATION_SERVICE_BASE_URL=http://notification-service.test",
        "minimall.auth.jwt.secret=test-gateway-route-jwt-secret",
        "minimall.auth.jwt.expire-seconds=3600"
})
class GatewayRoutesTest {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void configuresFrontendServiceRoutesWithEnvironmentDrivenUris() {
        Map<String, RouteDefinition> routes = routesById();

        assertRoute(routes, "user-service", "http://user-service.test",
                "/api/users/**", "/api/admin/login", "/api/admin/me", "/api/admin/audit-logs/**");
        assertRoute(routes, "product-service", "http://product-service.test",
                "/api/products/**", "/api/admin/products/**");
        assertRoute(routes, "inventory-service", "http://inventory-service.test",
                "/api/inventories/**", "/api/admin/inventories/**");
        assertRoute(routes, "order-service", "http://order-service.test",
                "/api/orders/**", "/api/admin/orders/**");
        assertRoute(routes, "payment-service", "http://payment-service.test",
                "/api/payments/**", "/api/admin/payments/**");
        assertRoute(routes, "notification-service", "http://notification-service.test",
                "/api/admin/notifications/**");
    }

    @Test
    void canonicalRoutesForwardWithoutLegacyRewriteFilters() {
        Map<String, RouteDefinition> routes = routesById();

        assertNoRewrite(routes, "user-service");
        assertNoRewrite(routes, "product-service");
        assertNoRewrite(routes, "inventory-service");
        assertNoRewrite(routes, "order-service");
        assertNoRewrite(routes, "payment-service");
        assertNoRewrite(routes, "notification-service");
    }

    @Test
    void doesNotConfigureInternalBrowserFacingRoutes() {
        List<String> allPathPredicates = routesById().values().stream()
                .flatMap(route -> pathPredicateArgs(route).stream())
                .toList();

        assertThat(allPathPredicates).noneMatch(path -> path.startsWith("/internal"));
    }

    @Test
    void doesNotConfigureLegacyServicePrefixRoutes() {
        List<String> allPathPredicates = routesById().values().stream()
                .flatMap(route -> pathPredicateArgs(route).stream())
                .toList();

        assertThat(allPathPredicates).doesNotContain(
                "/api/user/**",
                "/api/product/**",
                "/api/inventory/**",
                "/api/order/**",
                "/api/payment/**");
    }

    private Map<String, RouteDefinition> routesById() {
        return gatewayProperties.getRoutes().stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));
    }

    private void assertRoute(
            Map<String, RouteDefinition> routes,
            String routeId,
            String uri,
            String... pathPatterns) {
        RouteDefinition route = routes.get(routeId);
        assertThat(route).as("route " + routeId).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create(uri));
        assertThat(pathPredicateArgs(route)).contains(pathPatterns);
    }

    private void assertNoRewrite(
            Map<String, RouteDefinition> routes,
            String routeId) {
        RouteDefinition route = routes.get(routeId);
        assertThat(route).as("route " + routeId).isNotNull();
        assertThat(route.getFilters()).noneMatch(filter -> "RewritePath".equals(filter.getName()));
    }

    private List<String> pathPredicateArgs(RouteDefinition route) {
        return route.getPredicates().stream()
                .filter(predicate -> "Path".equals(predicate.getName()))
                .map(PredicateDefinition::getArgs)
                .flatMap(args -> args.values().stream())
                .toList();
    }
}
