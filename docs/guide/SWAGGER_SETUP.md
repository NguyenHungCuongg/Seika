# Swagger/OpenAPI Setup - Seika Microservices

This document describes the OpenAPI (formerly Swagger) setup for Seika microservices platform with code-first approach using Springdoc OpenAPI.

## Overview

The system implements **API Documentation Aggregation** pattern:

- Each microservice generates its own OpenAPI 3.0 documentation
- Central **Swagger UI** aggregates all APIs at the API Gateway
- Gateway routes requests for API docs from each microservice
- Single point of access for all API documentation

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Swagger UI (Central)                     │
│              http://localhost:8080/swagger-ui               │
└────────────────┬────────────────────────────────────────────┘
                 │
┌─────────────────────────────────────────────────────────────┐
│                  API Gateway (Port 8080)                    │
│                                                               │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  OpenAPI Doc Routes (v3/api-docs/{service-name})   │   │
│  │  - Proxies to individual microservice docs          │   │
│  │  - Aggregates in Swagger UI dropdown                │   │
│  └─────────────────────────────────────────────────────┘   │
└────────┬────────────────┬────────────────┬──────────────────┘
         │                │                │
   ┌─────────────┐  ┌──────────────┐  ┌────────────────┐
   │  Identity   │  │   Profile    │  │    Wallet      │
   │  Service    │  │   Service    │  │    Service     │
   │  (8081)     │  │   (8082)     │  │    (8084)      │
   │             │  │              │  │                │
   │ /swagger-ui │  │  /swagger-ui │  │  /swagger-ui   │
   │ /v3/api-docs│  │ /v3/api-docs │  │  /v3/api-docs  │
   └─────────────┘  └──────────────┘  └────────────────┘

   [Similar structure for Marketplace, Flashcard, Quiz, Notification]
```

## Setup Components

### 1) Springdoc OpenAPI Dependencies

Added to all microservices and API Gateway:

**For Microservices (MVC):**

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

**For API Gateway (WebFlux):**

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
    <version>2.3.0</version>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-api</artifactId>
    <version>2.3.0</version>
</dependency>
```

### 2) OpenAPI Configuration

**Microservices** (`OpenAPIConfig.java` in each service):

```java
@Configuration
public class OpenAPIConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .addServersItem(new Server()
                    .url("http://localhost:8080")
                    .description("API Gateway"))
                .addServersItem(new Server()
                    .url("http://identity-service:8081")
                    .description("Identity Service"))
                .info(new Info()
                    .title("Identity Service API")
                    .version("1.0.0")
                    .description("..."));
    }
}
```

**API Gateway** (`OpenAPIAggregationConfig.java`):

- Provides central OpenAPI definition
- Gateway routes aggregate docs from all services
- Swagger UI displays all APIs via dropdown menu

### 3) Application Configuration

**Microservices** (`application.yaml`):

```yaml
springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    display-request-duration: true
  api-docs:
    path: /v3/api-docs
```

**API Gateway** (`application.yaml`):

- Defines routes for each service's OpenAPI docs
- Swagger UI config with URLs for all services
- Routes use `RewritePath` filter to map `/v3/api-docs/service-name` to `/v3/api-docs`

```yaml
springdoc:
  swagger-ui:
    enabled: true
    urls:
      - name: Identity Service
        url: /v3/api-docs/identity-service
      - name: Profile Service
        url: /v3/api-docs/profile-service
      # ... other services
```

## Access Points

### Individual Microservice Swagger UI

Each microservice exposes its own Swagger UI (useful for development/testing):

- **Identity Service**: `http://localhost:8081/swagger-ui.html`
- **Profile Service**: `http://localhost:8082/swagger-ui.html`
- **Wallet Service**: `http://localhost:8084/swagger-ui.html`
- **Marketplace Service**: `http://localhost:8085/swagger-ui.html`
- **Flashcard Service**: `http://localhost:8086/swagger-ui.html`
- **Quiz Service**: `http://localhost:8087/swagger-ui.html`
- **Notification Service**: `http://localhost:8083/swagger-ui.html`

### Central Swagger UI (Recommended)

Access all APIs through the API Gateway:

**`http://localhost:8080/swagger-ui.html`**

This provides:

- Dropdown to select which microservice API to view
- Centralized documentation access
- Single authentication point (if gateway-level auth is implemented)
- Easier for API consumers

## API Documentation Endpoints

### OpenAPI JSON/YAML Formats

**Individual Microservice OpenAPI JSON:**

- `http://localhost:8081/v3/api-docs`
- `http://localhost:8082/v3/api-docs`
- etc.

**Via API Gateway (Aggregated):**

- `http://localhost:8080/v3/api-docs/identity-service`
- `http://localhost:8080/v3/api-docs/profile-service`
- `http://localhost:8080/v3/api-docs/marketplace-service`
- etc.

## Code-First Approach

Springdoc OpenAPI automatically generates documentation from your code:

### 1) Use Spring Web Annotations

```java
@RestController
@RequestMapping("/api/profiles")
public class UserProfileController {

    @PostMapping
    @Operation(summary = "Create user profile")
    public ResponseEntity<UserProfileResponse> createUserProfile(
            @RequestBody @Valid UserProfileRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userProfileService.createUserProfile(request));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get profile by user ID")
    public ResponseEntity<UserProfileResponse> getUserProfile(
            @PathVariable String userId) {
        return ResponseEntity.ok(userProfileService.getUserProfileByUserId(userId));
    }
}
```

### 2) Use OpenAPI Annotations (Optional, for enhancement)

```java
@PostMapping
@Operation(
    summary = "Create user profile",
    description = "Creates a new user profile with personal information",
    tags = {"Profile Management"}
)
@ApiResponse(
    responseCode = "201",
    description = "Profile created successfully"
)
@ApiResponse(
    responseCode = "409",
    description = "Profile already exists"
)
public ResponseEntity<UserProfileResponse> createUserProfile(
        @RequestBody @Valid @io.swagger.v3.oas.annotations.media.Content(
            mediaType = "application/json"
        ) UserProfileRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userProfileService.createUserProfile(request));
}
```

### 3) Document DTO Fields

```java
@Getter
@Setter
public class UserProfileRequest {
    @NotBlank(message = "User ID is required")
    @Schema(description = "Unique user identifier", example = "user-123")
    private String userId;

    @NotBlank(message = "Full name is required")
    @Schema(description = "User full name", example = "John Doe")
    private String fullName;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    @Schema(description = "User date of birth (YYYY-MM-DD)", example = "2000-01-15")
    private LocalDate dateOfBirth;

    @NotBlank
    @Schema(description = "User gender", example = "MALE", allowableValues = {"MALE", "FEMALE", "OTHER"})
    private String gender;

    @Schema(description = "User profile picture URL (optional)")
    private String profilePictureUrl;
}
```

## Benefits

✅ **Code-First Approach**

- Documentation generated from annotations
- Always up-to-date with code
- No manual YAML/JSON editing
- Single source of truth

✅ **Microservices Isolation**

- Each service owns its own API docs
- Independent documentation lifecycle
- Clear service boundaries

✅ **Centralized Access**

- Single Swagger UI for all APIs
- Easy for API consumers
- Organized by service via dropdown

✅ **Development Friendly**

- Quick API testing during development
- Try-it-out functionality in Swagger UI
- Request/response validation shown in docs

✅ **Production Ready**

- OpenAPI 3.0 standard compliant
- Consumable by code generators, API gateways, etc.
- Integration with monitoring/analytics tools

## Common Issues & Solutions

### Issue: Swagger UI not loading

**Solution:**

1. Ensure springdoc dependency is added to `pom.xml`
2. Check service is running: `http://localhost:PORT/swagger-ui.html`
3. Verify `springdoc.swagger-ui.enabled: true` in `application.yaml`

### Issue: No endpoints showing in Swagger UI

**Solution:**

1. Ensure controllers have proper annotations (`@RestController`, `@RequestMapping`)
2. Check routes are correctly defined
3. Restart service (annotation processing happens at startup)
4. Look at application logs for any OpenAPI config errors

### Issue: Gateway Swagger not showing service endpoints

**Solution:**

1. Verify all microservices are running and registered in Eureka
2. Check gateway routes are defined correctly for each `/v3/api-docs/{service}` path
3. Ensure Eureka discovery is working
4. Check gateway logs for routing errors

### Issue: Swagger UI dropdown shows only one service

**Solution:**

1. Verify all services in `springdoc.swagger-ui.urls` list in gateway `application.yaml`
2. Check microservice names match exactly (case-sensitive)
3. Verify all routes are properly configured in gateway routes

## OpenAPI Customization

### Add API Tags for Organization

```java
@RestController
@RequestMapping("/api/profiles")
@Tag(name = "Profile Management", description = "User profile management endpoints")
public class UserProfileController {
    // ...
}
```

### Add Request/Response Examples

```java
@PostMapping
@Operation(summary = "Create profile")
@io.swagger.v3.oas.annotations.parameters.RequestBody(
    content = @Content(
        mediaType = "application/json",
        examples = @ExampleObject(
            value = """
            {
                "userId": "user-123",
                "fullName": "John Doe",
                "dateOfBirth": "2000-01-15",
                "gender": "MALE"
            }"""
        )
    )
)
public ResponseEntity<UserProfileResponse> createUserProfile(
        @RequestBody UserProfileRequest request) {
    // ...
}
```

## Monitoring & Maintenance

- **Versioning**: Update `version` in `OpenAPIConfig.java` when API changes
- **Deprecation**: Mark deprecated endpoints with `@Deprecated` annotation
- **Documentation**: Keep endpoint descriptions updated alongside code changes
- **Testing**: Validate OpenAPI docs with endpoint integration tests

## References

- [Springdoc OpenAPI Documentation](https://springdoc.org/)
- [OpenAPI 3.0 Specification](https://spec.openapis.org/oas/v3.0.3)
- [Swagger UI Documentation](https://github.com/swagger-api/swagger-ui)

---

**Status**: ✅ Production Ready
**Last Updated**: 2026-05-28
**Version**: 1.0.0
