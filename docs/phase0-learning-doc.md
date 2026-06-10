# Phase 0: Foundation — Tài liệu học tập

> Tài liệu này giải thích TẤT CẢ những gì Phase 0 đã làm, tại sao làm, và kiến thức nào liên quan.
> Dành cho người đang học Java/Spring Boot từ đầu.

---

## 1. Dự án này là gì?

**Service Marketplace** — Nền tảng đặt dịch vụ đa nhà cung cấp.

**Ví dụ thực tế:** Giống Shopee nhưng thay vì mua sản phẩm, khách hàng đặt dịch vụ (spa, tư vấn, sửa xe...).

```
Vendor (người bán dịch vụ)  →  đăng service, quản lý booking, nhận tiền
Customer (khách hàng)       →  tìm dịch vụ, đặt lịch, thanh toán, đánh giá
Platform (hệ thống)         →  quản lý payment, commission, conflict
```

**Tại sao chọn project này?**
- Cover đủ OOP, Database Design, System Design, Payment
- Không giống tutorial (multi-vendor + escrow + booking conflict)
- Thực tế — có thể demo cho hiring manager

---

## 2. Kiến thức nền tảng Phase 0

### 2.1 Spring Boot là gì?

Spring Boot = framework để xây dựng ứng dụng Java nhanh.

**Không có Spring Boot** (Java thuần):
```java
// Phải tự setup Tomcat server, tự cấu hình database connection,
// tự handle HTTP request/response... hàng trăm dòng code config
public class Main {
    public static void main(String[] args) {
        Server server = new Tomcat(); // tự setup
        DataSource ds = new HikariDataSource(); // tự config
        // ... 100+ dòng setup code
    }
}
```

**Có Spring Boot:**
```java
@SpringBootApplication  // ← Annotation này tự động cấu hình tất cả
public class ServiceMarketplaceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceMarketplaceApplication.class, args);
        // Xong. Server chạy, config tự động.
    }
}
```

**`@SpringBootApplication` làm gì?**
- Quét tất cả classes trong cùng package và sub-packages
- Tự động cấu hình (auto-configuration): database, server, security, v.v.
- Phát hiện các dependency trong `pom.xml` và tự cấu hình phù hợp

### 2.2 Maven là gì?

Maven = công cụ quản lý dependency và build project.

**`pom.xml`** = Project Object Model — file config trung tâm.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Đoạn này nói: "Tôi cần Spring Web". Maven tự động download thư viện + tất cả thư viện phụ thuộc (transitive dependencies).

**`mvnw`** (Maven Wrapper) = script chạy Maven mà KHÔNG cần cài Maven trên máy. Tự download Maven nếu chưa có.

**Commands quan trọng:**
| Command | Chức năng |
|---------|----------|
| `./mvnw compile` | Biên dịch source code |
| `./mvnw test` | Chạy tests |
| `./mvnw spring-boot:run` | Khởi chạy ứng dụng |
| `./mvnw clean` | Xóa thư mục `target/` (build artifacts) |

### 2.3 Docker — Tại sao cần?

PostgreSQL và Redis là phần mềm chạy độc lập. Nếu cài trực tiếp lên máy:
- Phiền (phải cài đặt, cấu hình, cleanup)
- Có thể conflict với version khác
- Không reproduce được trên máy khác

**Docker** giải quyết bằng cách chạy phần mềm trong container — giống VM nhưng nhẹ hơn rất nhiều.

**`docker-compose.yml`** = file mô tả các services cần chạy:

```yaml
services:
  postgres:                          # Tên service
    image: postgres:16-alpine        # Image (bản nhẹ = alpine)
    ports:
      - "5433:5432"                  # Host port 5433 → Container port 5432
    environment:                     # Biến môi trường (config)
      POSTGRES_USER: marketplace
      POSTGRES_PASSWORD: marketplace
      POSTGRES_DB: marketplace
    mem_limit: 256m                  # Giới hạn RAM
```

**`postgres:16-alpine`** vs `postgres:16`:
- Alpine = bản tối giản của Linux (~80MB)
- Bình thường = Debian-based (~400MB)
- Dùng Alpine để tiết kiệm RAM/disk

**Port mapping `"5433:5432"`:**
- `5433` = port trên máy Hien (host)
- `5432` = port trong container
- Tại sao 5433? Vì Hien đã có Homebrew PostgreSQL chạy trên 5432

**Commands:**
| Command | Chức năng |
|---------|----------|
| `docker compose up -d` | Khởi động tất cả services (background) |
| `docker compose ps` | Xem trạng thái containers |
| `docker compose down -v` | Dừng và xóa containers + volumes |
| `docker compose logs` | Xem logs |

### 2.4 Application Profiles

Spring Boot hỗ trợ nhiều "profile" — mỗi profile là một bộ cấu hình khác nhau.

```
application.yml        ← Config chung (áp dụng mọi profile)
application-dev.yml    ← Chỉ chạy khi profile = "dev" (kết nối Docker)
application-test.yml   ← Chỉ chạy khi profile = "test" (H2 in-memory)
```

**Tại sao cần?**
- **Dev**: kết nối Docker PostgreSQL + Redis (thực tế)
- **Test**: dùng H2 in-memory database (nhanh, không cần Docker)

**Kích hoạt profile:**
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
./mvnw test -Dspring.profiles.active=test
```

### 2.5 Spring MVC — REST Controller

**REST API** = cách giao tiếp giữa client và server qua HTTP.

```
Client (React app)  →  GET /api/health  →  Server (Spring Boot)
                                                     ↓
Client nhận JSON    ←  {"status":"UP"}  ←  Controller xử lý
```

**`@RestController`** = đánh dấu class này xử lý HTTP requests và trả về JSON.

```java
@RestController                    // Annotation: Spring biết class này là REST controller
public class HealthController {

    @GetMapping("/api/health")     // Annotation: xử lý GET request tại URL này
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "service-marketplace");
        // Map.of() = tạo immutable map (Java 9+)
        // Spring tự convert Map → JSON
    }
}
```

**Luồng xử lý:**
```
HTTP GET /api/health
    → Tomcat (web server) nhận request
    → DispatcherServlet (Spring) tìm controller phù hợp
    → HealthController.health() được gọi
    → Trả về Map<String, String>
    → Jackson (JSON library) convert Map → JSON
    → HTTP response 200 với body {"status":"UP","service":"service-marketplace"}
```

### 2.6 Spring Security

Spring Security mặc định **BLOCK mọi request**. Nếu không cấu hình, `/api/health` sẽ trả về 401 Unauthorized.

```java
@Configuration                      // Class này chứa Spring beans (objects managed by Spring)
@EnableWebSecurity                  // Bật Spring Security
public class SecurityConfig {

    @Bean                           // Method trả về object được Spring quản lý
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                // STATELESS = không dùng HTTP session, mỗi request tự chứa auth info (JWT sau này)
            )
            .csrf(AbstractHttpConfigurer::disable)
            // CSRF = Cross-Site Request Forgery protection
            // Cần cho web truyền thống (form submit + cookies)
            // KHÔNG cần cho REST API dùng JWT tokens
            .authorizeHttpRequests(auth ->
                auth.anyRequest().permitAll()
                // Phase 0: cho phép tất cả requests. Phase 2 sẽ restrict.
            );
        return http.build();
    }
}
```

### 2.7 JPA + Hibernate + Flyway

**JPA** (Jakarta Persistence API) = chuẩn Java để làm việc với database bằng objects thay vì SQL.

**Hibernate** = implementation phổ biến nhất của JPA.

```
Java Object (Entity)  ←→  Database Table
User.java             ←→  users table
Booking.java          ←→  bookings table
```

**Flyway** = công cụ quản lý database migrations (thay đổi schema qua thời gian).

```
db/migration/
├── V1__create_users_table.sql       ← Chạy lần đầu
├── V2__create_vendors.sql           ← Chạy lần đầu
└── V3__add_email_to_users.sql       ← Chạy lần đầu
```

**Tại sao dùng Flyway thay vì `hibernate.ddl-auto=create`?**
- `create` = xóa hết data và tạo lại mỗi lần restart → mất data
- `validate` = chỉ kiểm tra entity matching với schema, KHÔNG tự sửa → an toàn
- Flyway quản lý thay đổi schema qua versioned SQL scripts → reproduce được, auditable

**Trong Phase 0:** Chưa có entity nào, Flyway chỉ tạo bảng `flyway_schema_history` để theo dõi migrations.

### 2.8 Actuator

Spring Boot Actuator = monitoring endpoints có sẵn.

```
/actuator/health  →  Trạng thái ứng dụng (UP/DOWN)
/actuator/info    →  Thông tin ứng dụng
/actuator/metrics →  Các chỉ số performance
```

Trong Phase 0, chỉ expose `health` và `info` (không expose tất cả vì lý do bảo mật).

---

## 3. Cấu trúc project giải thích

```
service-marketplace/
├── pom.xml                    # Maven config — danh sách dependencies
├── mvnw / mvnw.cmd            # Maven Wrapper — chạy Maven không cần cài
├── docker-compose.yml         # Docker services — PostgreSQL + Redis
├── .env.example               # Template cho environment variables
├── .env                       # Local credentials (KHÔNG push lên GitHub)
│
├── src/main/java/com/hien/marketplace/
│   ├── ServiceMarketplaceApplication.java   # Entry point — hàm main()
│   ├── config/                              # Cấu hình Spring
│   │   └── SecurityConfig.java              # Security rules
│   └── interfaces/                          # Layer giao tiếp với bên ngoài
│       └── rest/                            # REST API controllers
│           └── HealthController.java        # Health endpoint
│
├── src/main/resources/
│   ├── application.yml         # Config chung
│   ├── application-dev.yml     # Config cho môi trường dev
│   ├── application-test.yml    # Config cho test
│   └── db/migration/           # Flyway SQL migrations (rỗng Phase 0)
│
└── src/test/java/              # Test code
    └── .../ServiceMarketplaceApplicationTests.java  # Smoke test
```

**Layered Architecture** (sẽ rõ hơn ở Phase 1+):
```
interfaces/    ← Nhận HTTP requests, trả responses (controllers)
application/   ← Business use cases, DTOs (sẽ tạo ở Phase 2)
domain/        ← Business logic, entities, value objects (Phase 1)
infrastructure/ ← Kết nối bên ngoài: DB, Redis, Stripe (Phase 1+)
config/        ← Spring configuration
```

---

## 4. Dependencies trong pom.xml — Tác dụng từng cái

| Dependency | Tác dụng | Dùng khi nào |
|------------|----------|-------------|
| `spring-boot-starter-web` | REST API, HTTP server (Tomcat) | Mọi phase |
| `spring-boot-starter-data-jpa` | Làm việc với database bằng Java objects | Phase 1+ |
| `spring-boot-starter-data-redis` | Kết nối Redis cho caching | Phase 5 |
| `spring-boot-starter-security` | Xác thực + phân quyền | Phase 2 |
| `spring-boot-starter-validation` | Validate input (email, required fields...) | Phase 2+ |
| `spring-boot-starter-actuator` | Monitoring endpoints (/actuator/health) | Mọi phase |
| `spring-boot-devtools` | Hot reload khi code (auto restart) | Dev only |
| `flyway-core` + `flyway-database-postgresql` | Quản lý database schema qua SQL scripts | Phase 1+ |
| `postgresql` | JDBC driver kết nối PostgreSQL | Mọi phase |
| `lombok` | Giảm boilerplate code (getter/setter/constructor tự động) | Mọi phase |
| `h2` | In-memory database cho test (không cần Docker) | Test only |

---

## 5. Luồng hoạt động Phase 0

```
1. Developer chạy: docker compose up -d
   → PostgreSQL chạy trên localhost:5433
   → Redis chạy trên localhost:6379

2. Developer chạy: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   → Maven compile source code
   → Spring Boot khởi động
   → Kết nối PostgreSQL (thông qua HikariCP connection pool)
   → Flyway kiểm tra migrations (Phase 0: chưa có, chỉ tạo schema history table)
   → Hibernate khởi tạo (Phase 0: chưa có entity nào)
   → Tomcat web server chạy trên port 8080
   → Spring Security cho phép tất cả requests
   → App sẵn sàng nhận HTTP requests

3. Client gọi: GET http://localhost:8080/api/health
   → DispatcherServlet tìm HealthController
   → health() trả về Map.of("status", "UP")
   → Jackson convert → JSON
   → Response: {"status":"UP","service":"service-marketplace"}

4. Client gọi: GET http://localhost:8080/actuator/health
   → Actuator kiểm tra: database connection OK? Redis connection OK?
   → Response: {"status":"UP"}
```

---

## 6. Git Flow đã dùng

```
main (stable)
  │
  ├── branch: feat/phase0-foundation
  │     ├── commit: feat: Phase 0 — Spring Boot foundation...
  │     └── PR #1 → squash merge vào main
  │
  └── commit: docs: mark Phase 0 complete...
```

**Squash merge** = gộp tất cả commits trên branch thành 1 commit trên main. Giữ history sạch.

---

## 7. Concepts cần hiểu trước Phase 1

Phase 1 sẽ tạo domain entities + database tables. Trước khi bắt đầu, Hien nên hiểu:

### Java OOP Basics
- **Class vs Object**: Class = bản thiết kế, Object = instance cụ thể
- **Encapsulation**: Private fields + public methods
- **Constructor**: Hàm khởi tạo object
- **Enum**: Tập hợp hằng số có tên (e.g., `BookingStatus.PENDING`)

### JPA Basics
- **Entity** = Java class map với database table (`@Entity`)
- **Primary Key** = `@Id @GeneratedValue`
- **Relationships** = `@OneToMany`, `@ManyToOne`, `@ManyToMany`
- **Column mapping** = `@Column(name = "...", nullable = false)`

### SQL Basics
- `CREATE TABLE`, `ALTER TABLE`
- Foreign keys, unique constraints, indexes
- Data types: VARCHAR, BIGINT, TIMESTAMP, BOOLEAN

### Đọc thêm (recommended)
- [Spring Boot Official Guide](https://spring.io/guides/gs/rest-service)
- [Baeldung Spring Boot tutorials](https://www.baeldung.com/spring-boot)
- [JPA/Hibernate basics](https://www.baeldung.com/learn-jpa-hibernate)
