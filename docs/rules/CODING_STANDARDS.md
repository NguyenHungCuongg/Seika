# Nguyên Tắc Phát Triển Seika Microservices

## Mục Lục

1. [Quy Tắc Đặt Tên](#quy-tắc-đặt-tên)
2. [Cấu Trúc Project](#cấu-trúc-project)
3. [REST API Standards](#rest-api-standards)
4. [Request/Response Pattern](#requestresponse-pattern)
5. [Exception Handling](#exception-handling)
6. [Cấu Trúc Controller](#cấu-trúc-controller)
7. [Cấu Trúc Service](#cấu-trúc-service)
8. [Database Layer](#database-layer)
9. [Validation](#validation)
10. [Logging](#logging)

---

## 1. Quy Tắc Đặt Tên

### 1.1 Package Naming

```
com.seika.{service-name}.{layer}

Ví dụ:
- com.seika.flashcard-service.controller
- com.seika.flashcard-service.service
- com.seika.flashcard-service.repository
- com.seika.flashcard-service.entity
- com.seika.flashcard-service.dto
- com.seika.flashcard-service.exception
- com.seika.flashcard-service.config
- com.seika.flashcard-service.util
```

### 1.2 Class Naming

```
Entity:           Entity, Flashcard, User, Quiz
DTO:              FlashcardDTO, FlashcardRequest, FlashcardResponse
Controller:       FlashcardController, UserController
Service:          FlashcardService, UserService
Repository:       FlashcardRepository, UserRepository
Exception:        FlashcardNotFoundException, InvalidRequestException
Config:           MongoConfig, JwtConfig
Utility:          DateUtil, StringUtil, ValidationUtil
```

### 1.3 Method Naming

```
GET:      getById(), getAll(), findByName(), search()
POST:     create(), save(), add()
PUT:      update(), modify()
PATCH:    patch(), partialUpdate()
DELETE:   delete(), remove()
```

### 1.4 Variable Naming

```
Local variables:      userId, flashcardId, userName (camelCase)
Constants:            MAX_PAGE_SIZE, DEFAULT_TIMEOUT (UPPER_SNAKE_CASE)
Boolean variables:    isActive, hasPermission, canDelete (is/has/can prefix)
Collections:          users, flashcards (plural form)
```

### 1.5 Property Naming

```
createdAt:       Thời gian tạo
updatedAt:       Thời gian cập nhật
createdBy:       Người tạo
updatedBy:       Người cập nhật
isActive:        Trạng thái hoạt động
deletedAt:       Thời gian xóa mềm (soft delete)
```

---

## 2. Cấu Trúc Project

```
flashcard-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/seika/flashcard_service/
│   │   │       ├── controller/
│   │   │       ├── service/
│   │   │       ├── repository/
│   │   │       ├── entity/
│   │   │       ├── dto/
│   │   │       ├── exception/
│   │   │       ├── config/
│   │   │       ├── util/
│   │   │       ├── mapper/
│   │   │       └── FlashcardServiceApplication.java
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── application-{profile}.yaml
│   └── test/
│       └── java/
│           └── com/seika/flashcard_service/
├── pom.xml
└── README.md
```

---

## 3. REST API Standards

### 3.1 Endpoint Conventions

```
GET     /api/v1/{resource}              - Lấy tất cả
GET     /api/v1/{resource}/{id}         - Lấy chi tiết
POST    /api/v1/{resource}              - Tạo mới
PUT     /api/v1/{resource}/{id}         - Cập nhật toàn bộ
PATCH   /api/v1/{resource}/{id}         - Cập nhật một phần
DELETE  /api/v1/{resource}/{id}         - Xóa

Ví dụ:
GET     /api/v1/flashcards
GET     /api/v1/flashcards/{id}
POST    /api/v1/flashcards
PUT     /api/v1/flashcards/{id}
PATCH   /api/v1/flashcards/{id}
DELETE  /api/v1/flashcards/{id}
```

### 3.2 Query Parameters

```
Pagination:
?page=0&size=10&sort=createdAt,desc

Search:
?search=keyword&filter=status:active

Filtering:
?status=active&category=business
```

### 3.3 HTTP Status Codes

```
200 OK              - Request thành công, có dữ liệu trả về
201 Created         - Resource được tạo thành công
204 No Content      - Request thành công, không có dữ liệu trả về
400 Bad Request     - Request không hợp lệ (validation error)
401 Unauthorized    - Không xác thực
403 Forbidden       - Không có quyền
404 Not Found       - Resource không tồn tại
409 Conflict        - Xung đột dữ liệu
500 Internal Server Error - Lỗi server
```

---

## 4. Request/Response Pattern

### 4.1 DTO Structure

#### Request DTO

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardRequest {
    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(min = 3, max = 255, message = "Tiêu đề phải từ 3-255 ký tự")
    private String title;

    @NotBlank(message = "Nội dung không được để trống")
    private String content;

    @NotNull(message = "Category không được để trống")
    private String categoryId;

    private String tags;

    @NotNull(message = "Độ khó phải từ 1-5")
    @Min(1)
    @Max(5)
    private Integer difficulty;
}
```

#### Response DTO

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardResponse {
    private String id;
    private String title;
    private String content;
    private String categoryId;
    private String tags;
    private Integer difficulty;
    private Integer views;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
```

### 4.2 Standard Response Wrapper

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "Success", data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, message, data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "Created", data, LocalDateTime.now());
    }
}
```

### 4.3 Paginated Response

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean isLast;
    private LocalDateTime timestamp;

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isLast(),
            LocalDateTime.now()
        );
    }
}
```

---

## 5. Exception Handling

### 5.1 Custom Exceptions

```java
// Base Exception
public class SeikaSException extends RuntimeException {
    private final int code;
    private final String message;

    public SeikaSException(int code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
}

// Specific Exceptions
public class ResourceNotFoundException extends SeikaSException {
    public ResourceNotFoundException(String resource, String id) {
        super(404, resource + " với ID " + id + " không tồn tại");
    }
}

public class InvalidRequestException extends SeikaSException {
    public InvalidRequestException(String message) {
        super(400, message);
    }
}

public class UnauthorizedException extends SeikaSException {
    public UnauthorizedException(String message) {
        super(401, message);
    }
}

public class ForbiddenException extends SeikaSException {
    public ForbiddenException(String message) {
        super(403, message);
    }
}
```

### 5.2 Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(404, ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidRequest(
            InvalidRequestException ex,
            HttpServletRequest request) {
        log.error("Invalid request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors()
            .stream()
            .map(DefaultMessageSourceResolvable::getDefaultMessage)
            .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(400, "Validation error: " + message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGenericException(
            Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(500, "Có lỗi xảy ra. Vui lòng thử lại sau"));
    }
}
```

---

## 6. Cấu Trúc Controller

### 6.1 Controller Template

```java
@RestController
@RequestMapping("/api/v1/flashcards")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Flashcard", description = "Flashcard Management API")
public class FlashcardController {

    private final FlashcardService flashcardService;

    @GetMapping
    @Operation(summary = "Lấy danh sách flashcards")
    @Parameters({
        @Parameter(name = "page", description = "Trang (0-indexed)"),
        @Parameter(name = "size", description = "Số lượng/trang"),
        @Parameter(name = "sort", description = "Sắp xếp (field,direction)")
    })
    public ResponseEntity<ApiResponse<PagedResponse<FlashcardResponse>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        try {
            log.info("Fetching flashcards - page: {}, size: {}", page, size);
            PagedResponse<FlashcardResponse> response = flashcardService.getAll(page, size, sort);
            return ResponseEntity.ok(ApiResponse.success(response, "Danh sách flashcards"));
        } catch (Exception ex) {
            log.error("Error fetching flashcards", ex);
            throw new SeikaSException(500, "Lỗi lấy danh sách flashcards");
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết flashcard")
    public ResponseEntity<ApiResponse<FlashcardResponse>> getById(
            @PathVariable String id) {
        log.info("Fetching flashcard with id: {}", id);
        FlashcardResponse response = flashcardService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping
    @Operation(summary = "Tạo flashcard mới")
    public ResponseEntity<ApiResponse<FlashcardResponse>> create(
            @Valid @RequestBody FlashcardRequest request) {
        log.info("Creating new flashcard: {}", request.getTitle());
        FlashcardResponse response = flashcardService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật flashcard")
    public ResponseEntity<ApiResponse<FlashcardResponse>> update(
            @PathVariable String id,
            @Valid @RequestBody FlashcardRequest request) {
        log.info("Updating flashcard with id: {}", id);
        FlashcardResponse response = flashcardService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Cập nhật thành công"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa flashcard")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String id) {
        log.info("Deleting flashcard with id: {}", id);
        flashcardService.delete(id);
        return ResponseEntity.noContent()
            .build();
    }
}
```

---

## 7. Cấu Trúc Service

### 7.1 Service Interface

```java
public interface FlashcardService {
    PagedResponse<FlashcardResponse> getAll(int page, int size, String sort);
    FlashcardResponse getById(String id);
    FlashcardResponse create(FlashcardRequest request);
    FlashcardResponse update(String id, FlashcardRequest request);
    void delete(String id);
}
```

### 7.2 Service Implementation

```java
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class FlashcardServiceImpl implements FlashcardService {

    private final FlashcardRepository repository;
    private final FlashcardMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FlashcardResponse> getAll(int page, int size, String sort) {
        log.debug("Getting all flashcards - page: {}, size: {}", page, size);

        Sort sortOrder = Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequest.of(page, size, sortOrder);

        Page<Flashcard> flashcards = repository.findAll(pageable);
        Page<FlashcardResponse> response = flashcards.map(mapper::toResponse);

        return PagedResponse.of(response);
    }

    @Override
    @Transactional(readOnly = true)
    public FlashcardResponse getById(String id) {
        log.debug("Getting flashcard by id: {}", id);
        Flashcard flashcard = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Flashcard", id));
        return mapper.toResponse(flashcard);
    }

    @Override
    public FlashcardResponse create(FlashcardRequest request) {
        log.info("Creating flashcard: {}", request.getTitle());

        Flashcard flashcard = mapper.toEntity(request);
        flashcard.setCreatedAt(LocalDateTime.now());

        Flashcard saved = repository.save(flashcard);
        log.info("Flashcard created with id: {}", saved.getId());

        return mapper.toResponse(saved);
    }

    @Override
    public FlashcardResponse update(String id, FlashcardRequest request) {
        log.info("Updating flashcard: {}", id);

        Flashcard flashcard = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Flashcard", id));

        mapper.updateEntity(request, flashcard);
        flashcard.setUpdatedAt(LocalDateTime.now());

        Flashcard updated = repository.save(flashcard);
        log.info("Flashcard updated: {}", id);

        return mapper.toResponse(updated);
    }

    @Override
    public void delete(String id) {
        log.info("Deleting flashcard: {}", id);

        Flashcard flashcard = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Flashcard", id));

        repository.delete(flashcard);
        log.info("Flashcard deleted: {}", id);
    }
}
```

---

## 8. Database Layer

### 8.1 Entity Structure

```java
@Entity
@Document(collection = "flashcards")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Flashcard {

    @Id
    private String id;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    @NotNull
    private String categoryId;

    private String tags;

    @Min(1)
    @Max(5)
    private Integer difficulty;

    @Builder.Default
    private Integer views = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private String createdBy;
    private String updatedBy;

    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime deletedAt;
}
```

### 8.2 Repository Pattern

```java
@Repository
public interface FlashcardRepository extends MongoRepository<Flashcard, String> {
    List<Flashcard> findByCategoryIdAndIsActiveTrue(String categoryId);
    Page<Flashcard> findByTitleContainingIgnoreCaseAndIsActiveTrue(String title, Pageable pageable);
    Optional<Flashcard> findByIdAndIsActiveTrue(String id);
}
```

---

## 9. Validation

### 9.1 Input Validation

```
@NotNull       - Không được null
@NotBlank      - Không được trống (xóa khoảng trắng)
@NotEmpty      - Không được rỗng
@Size          - Kích thước chuỗi/tập hợp
@Min/@Max      - Giá trị số tối thiểu/tối đa
@Pattern       - Khớp regex
@Email         - Định dạng email
@URL           - Định dạng URL
@Range         - Khoảng giá trị
@Positive      - Số dương
```

### 9.2 Validation Example

```java
@Data
public class UserRequest {
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Password không được để trống")
    @Size(min = 8, max = 100, message = "Password phải từ 8-100 ký tự")
    private String password;

    @NotBlank(message = "Tên người dùng không được để trống")
    @Size(min = 3, max = 50, message = "Tên người dùng phải từ 3-50 ký tự")
    private String username;

    @Min(value = 18, message = "Tuổi phải >= 18")
    private Integer age;
}
```

---

## 10. Logging

### 10.1 Logging Levels

```
TRACE  - Chi tiết cực kỳ chi tiết (hiếm dùng)
DEBUG  - Thông tin debug (thường trong dev)
INFO   - Thông tin chung (luôn ghi)
WARN   - Cảnh báo (tiềm ẩn vấn đề)
ERROR  - Lỗi (cần xử lý)
FATAL  - Lỗi nghiêm trọng (hiếm dùng)
```

### 10.2 Logging Best Practices

```java
// ✅ GOOD
@Slf4j
public class FlashcardServiceImpl {
    public FlashcardResponse create(FlashcardRequest request) {
        log.info("Creating flashcard: {}", request.getTitle());
        // ...
        log.debug("Flashcard saved with id: {}", saved.getId());
        return mapper.toResponse(saved);
    }
}

// ❌ BAD
public void create(FlashcardRequest request) {
    System.out.println("Creating flashcard");
    // No structured logging
}
```

### 10.3 Application Properties

```yaml
logging:
  level:
    root: INFO
    com.seika: DEBUG
    org.springframework.web: INFO
    org.springframework.security: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: logs/application.log
```

---

## Tóm Tắt Nguyên Tắc Chính

| Khía Cạnh       | Nguyên Tắc                                                        |
| --------------- | ----------------------------------------------------------------- |
| **Đặt tên**     | camelCase (biến), PascalCase (class), UPPER_SNAKE_CASE (constant) |
| **Endpoint**    | `/api/v1/{resource}`                                              |
| **Response**    | Luôn dùng `ApiResponse<T>` wrapper                                |
| **Exception**   | Dùng custom exceptions kế thừa từ `SeikaSException`               |
| **Service**     | 1 Service = 1 Entity                                              |
| **DTO**         | Tách biệt Request/Response DTOs                                   |
| **Validation**  | Dùng Bean Validation annotations                                  |
| **Logging**     | Dùng `@Slf4j` + structured logging                                |
| **Transaction** | `@Transactional` trên Service                                     |
| **Repository**  | Spring Data Repository + custom queries khi cần                   |

---

**Phiên bản**: 1.0
**Cập nhật lần cuối**: 2024
**Áp dụng cho**: Tất cả các services trong Seika Microservices
