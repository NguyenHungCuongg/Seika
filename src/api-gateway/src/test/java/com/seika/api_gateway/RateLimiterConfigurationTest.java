package com.seika.api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterConfigurationTest {

    private static final String GATEWAY_CONFIG_PREFIX = "spring.cloud.gateway.server.webflux";

    @Test
    void configuresExactlyOneRateLimiterForEveryRoute() throws IOException {
        GatewayProperties gatewayProperties = loadDevelopmentGatewayProperties();

        assertThat(gatewayProperties.getRoutes()).allSatisfy(route ->
                assertThat(effectiveRateLimiters(gatewayProperties, route))
                        .as("rate limiters for route %s", route.getId())
                        .hasSize(1)
        );
    }

    @Test
    void configuresGeneralRateLimiterForNonAuthenticationRoutes() throws IOException {
        GatewayProperties gatewayProperties = loadDevelopmentGatewayProperties();
        RouteDefinition flashcardRoute = findRoute(gatewayProperties, "flashcard-service");
        FilterDefinition rateLimiter = effectiveRateLimiters(gatewayProperties, flashcardRoute).getFirst();

        assertThat(rateLimiter.getArgs())
                .containsEntry("redis-rate-limiter.replenishRate", "50")
                .containsEntry("redis-rate-limiter.burstCapacity", "100")
                .containsEntry("key-resolver", "#{@userKeyResolver}");
    }

    @Test
    void configuresStricterRateLimiterForAuthenticationRoute() throws IOException {
        GatewayProperties gatewayProperties = loadDevelopmentGatewayProperties();
        RouteDefinition authenticationRoute = findRoute(gatewayProperties, "identity-auth-route");
        List<FilterDefinition> rateLimiters = effectiveRateLimiters(gatewayProperties, authenticationRoute);

        assertThat(rateLimiters).hasSize(1);
        assertThat(rateLimiters.getFirst().getArgs())
                .containsEntry("redis-rate-limiter.replenishRate", "10")
                .containsEntry("redis-rate-limiter.burstCapacity", "20")
                .containsEntry("key-resolver", "#{@ipKeyResolver}");
    }

    private GatewayProperties loadDevelopmentGatewayProperties() throws IOException {
        return loadGatewayProperties("api-gateway-dev.yaml", "api-gateway.yaml");
    }

    private RouteDefinition findRoute(GatewayProperties gatewayProperties, String routeId) {
        return gatewayProperties.getRoutes().stream()
                .filter(route -> routeId.equals(route.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gateway route is not configured: " + routeId));
    }

    private List<FilterDefinition> effectiveRateLimiters(
            GatewayProperties gatewayProperties,
            RouteDefinition route
    ) {
        return Stream.concat(
                        gatewayProperties.getDefaultFilters().stream(),
                        route.getFilters().stream()
                )
                .filter(filter -> "RequestRateLimiter".equals(filter.getName()))
                .toList();
    }

    private GatewayProperties loadGatewayProperties(String... configFiles) throws IOException {
        MutablePropertySources propertySources = new MutablePropertySources();
        YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

        for (String configFile : configFiles) {
            List<PropertySource<?>> yamlSources = yamlLoader.load(
                    configFile,
                    new FileSystemResource(locateGatewayConfig(configFile))
            );
            yamlSources.forEach(propertySources::addLast);
        }

        return new Binder(ConfigurationPropertySources.from(propertySources))
                .bind(GATEWAY_CONFIG_PREFIX, Bindable.of(GatewayProperties.class))
                .orElseThrow(() -> new IllegalStateException("Gateway properties are not configured"));
    }

    private Path locateGatewayConfig(String configFile) {
        List<Path> candidates = List.of(
                Path.of("src", "config-service", "src", "main", "resources", "configs", configFile),
                Path.of("..", "config-service", "src", "main", "resources", "configs", configFile)
        );

        return candidates.stream()
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot locate Config Server " + configFile));
    }
}