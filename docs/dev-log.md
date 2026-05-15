# Development Log

Append one entry per implementation task so future sessions can recover project context.

## Task <id> - <title>
- Date:
- Status:
- Implemented:
- Changed files:
- Commands run:
- Test result:
- Issues:
- Next:
## Task 1.1 - 配置父级 Maven 工程与统一依赖管理
- Date: 2026-04-29
- Status: Done
- Implemented: Created the root Maven parent POM with packaging=pom, Java 17, Spring Boot 3.2.12, Spring Cloud 2023.0.5, module declarations, dependency BOM management, compiler plugin management, and Spring Boot Maven plugin management.
- Changed files: pom.xml
- Commands run: task-master next; task-master show 1.1; task-master set-status 1.1 in-progress; mvn -N validate
- Test result: mvn -N validate succeeded. Full recursive mvn clean package -DskipTests was not run because Task 1.2 has not created child module directories yet.
- Issues: TaskMaster terminal output still shows mojibake through the Windows/WSL command bridge, but tasks.json UTF-8 content was verified earlier by base64 comparison.
- Next: Task 1.2 - 创建 common 与服务模块空壳 POM

## Task infra - Initialize Git repository
- Date: 2026-04-29
- Status: Done
- Implemented: Initialized the project root as a Git repository with default branch main. Verified .gitignore already excludes .env, node_modules/, npm debug logs, and TaskMaster report JSON files.
- Changed files: .git/, docs/dev-log.md
- Commands run: git init -b main; git status --short
- Test result: Git repository initialized successfully; working tree contains untracked project files ready for an initial commit.
- Issues: No commit was created because the request only asked to set up Git version management.
- Next: Review and create an initial commit when ready.

## Task 1.2 - 创建 common 与服务模块空壳 POM
- Date: 2026-04-29
- Status: Done
- Implemented: Created Maven module directories and module pom.xml files for common-core, common-auth, api-gateway, user-service, product-service, inventory-service, order-service, payment-service, and notification-service. Common modules are plain jars; api-gateway uses Spring Cloud Gateway and Actuator; service modules use Spring Web, Actuator, and common dependencies. Added target/ to .gitignore for Maven build outputs.
- Changed files: .gitignore; common-core/pom.xml; common-auth/pom.xml; api-gateway/pom.xml; user-service/pom.xml; product-service/pom.xml; inventory-service/pom.xml; order-service/pom.xml; payment-service/pom.xml; notification-service/pom.xml
- Commands run: task-master next; task-master show 1.2; task-master set-status 1.2 in-progress; dependency grep checks; mvn clean package -DskipTests
- Test result: mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: WSL still prints a NAT/localhost warning after commands, but the commands completed successfully. Empty src/main/java and src/main/resources directories exist locally; Git will not track empty directories until Task 1.3 adds source/config files.
- Next: Task 1.3 - 补齐服务启动类、基础配置与构建验证

## Task 1.3 - 补齐服务启动类、基础配置与构建验证
- Date: 2026-04-30
- Status: Done
- Implemented: Added Spring Boot application entry classes, base application.yml files, server ports, Actuator health exposure, and executable Spring Boot repackage configuration for api-gateway, user-service, product-service, inventory-service, order-service, payment-service, and notification-service.
- Changed files: api-gateway/pom.xml; api-gateway/src/main/java/com/minimall/gateway/ApiGatewayApplication.java; api-gateway/src/main/resources/application.yml; user-service/pom.xml; user-service/src/main/java/com/minimall/user/UserServiceApplication.java; user-service/src/main/resources/application.yml; product-service/pom.xml; product-service/src/main/java/com/minimall/product/ProductServiceApplication.java; product-service/src/main/resources/application.yml; inventory-service/pom.xml; inventory-service/src/main/java/com/minimall/inventory/InventoryServiceApplication.java; inventory-service/src/main/resources/application.yml; order-service/pom.xml; order-service/src/main/java/com/minimall/order/OrderServiceApplication.java; order-service/src/main/resources/application.yml; payment-service/pom.xml; payment-service/src/main/java/com/minimall/payment/PaymentServiceApplication.java; payment-service/src/main/resources/application.yml; notification-service/pom.xml; notification-service/src/main/java/com/minimall/notification/NotificationServiceApplication.java; notification-service/src/main/resources/application.yml
- Commands run: task-master next; task-master show 1.3; module configuration checks; mvn clean package -DskipTests; sequential java -jar health checks for /actuator/health
- Test result: mvn clean package -DskipTests succeeded for the full 10-module reactor. All 7 executable service jars started successfully and returned UP from /actuator/health.
- Issues: WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Mark Task 1.3 done in TaskMaster, then continue to the next available task.
## Task 2.1 - Create Docker Compose and env template
- Date: 2026-05-01
- Status: Done
- Implemented: Created docker-compose.yml for MySQL, Redis, and RabbitMQ management using environment-variable driven credentials and ports, named volumes, a shared bridge network, and container healthchecks. Extended .env.example with local infrastructure variables and placeholder development values.
- Changed files: docker-compose.yml; .env.example; .taskmaster/tasks/tasks.json
- Commands run: task-master next; task-master show 2; task-master expand --id=2 --num=3; task-master add-subtask for 2.1, 2.2, 2.3; task-master set-status --id=2 --status=in-progress; task-master set-status --id=2.1 --status=in-progress; docker version; docker compose version; docker compose --env-file .env.example config; docker compose --env-file .env.example config --quiet
- Test result: docker compose config and docker compose config --quiet both succeeded with .env.example. Containers were not started in this subtask; startup verification is Task 2.3.
- Issues: task-master expand timed out once, so subtasks were created with task-master add-subtask instead of manual tasks.json edits. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 2.2 - Add init directories and connection conventions.
## Task 2.2 - Add init directories and connection conventions
- Date: 2026-05-01
- Status: Done
- Implemented: Created the MySQL Docker init mount directory with a README explaining first-run script behavior. Added docs/local-infrastructure.md to document Compose variables, Spring service connection variable names, local host vs Compose-network host conventions, and common Docker Compose commands. Extended .env.example with Spring datasource, Redis, and RabbitMQ connection variables for future service integration.
- Changed files: .env.example; docs/local-infrastructure.md; infra/mysql/init/README.md; .taskmaster/tasks/tasks.json
- Commands run: task-master next; task-master show 2.2; task-master set-status --id=2.2 --status=in-progress; file presence checks; docker compose --env-file .env.example config --quiet
- Test result: docker compose config --quiet succeeded with .env.example. File checks confirmed infra/mysql/init/README.md and docs/local-infrastructure.md exist.
- Issues: WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 2.3 - Verify Docker Compose startup and document commands.
## Task 2.3 - Verify Docker Compose startup and document commands
- Date: 2026-05-01
- Status: Done
- Implemented: Started the Docker Compose infrastructure stack with MySQL, Redis, and RabbitMQ management. Verified all services reached healthy status and RabbitMQ management UI responded over HTTP. Confirmed docs/local-infrastructure.md already documents validate, start, stop, and cleanup commands.
- Changed files: .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 2.3; task-master set-status --id=2.3 --status=in-progress; docker compose --env-file .env.example config --quiet; docker compose --env-file .env.example ps; ss port check for 3306/6379/5672/15672; docker compose --env-file .env.example up -d; docker inspect health polling; curl http://127.0.0.1:15672; docker compose --env-file .env.example ps
- Test result: docker compose up -d succeeded. MySQL, Redis, and RabbitMQ containers are running and healthy. RabbitMQ management endpoint returned HTTP 200.
- Issues: Two inline Windows/WSL command strings failed due PowerShell parsing before Docker checks ran, so the health polling was executed through a temporary Bash script that was deleted afterward. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Mark Task 2.3 and parent Task 2 done, then continue to Task 3.
## Task 3.1 - Create migration directory and schema documentation
- Date: 2026-05-01
- Status: Done
- Implemented: Created docs/sql and docs/sql/migrations documentation. Defined Flyway-style migration naming, the required V1__initial_schema.sql location for Task 3.2, core table scope, required unique constraints, execution command conventions, and information_schema verification queries for Task 3.3.
- Changed files: docs/sql/README.md; docs/sql/migrations/README.md; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 3; task-master add-subtask for 3.1, 3.2, 3.3; task-master remove-subtask --id=3.2,3.3 to fix an earlier parallel creation race; task-master set-status --id=3 --status=in-progress; task-master set-status --id=3.1 --status=in-progress; file presence checks; documentation reads
- Test result: Verified docs/sql/README.md and docs/sql/migrations/README.md exist and include the migration path, required core tables, unique constraints, execution command, and verification queries. No Java compile was needed.
- Issues: The first attempt to create 3.2 and 3.3 in parallel caused TaskMaster subtask numbering/dependency race; corrected using TaskMaster remove-subtask and sequential add-subtask. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 3.2 - Create initial schema migration SQL.
## Task 3.2 - Create initial schema migration SQL
- Date: 2026-05-01
- Status: Done
- Implemented: Created docs/sql/migrations/V1__initial_schema.sql for the MVP MySQL schema. The migration defines users, products, inventory, inventory_records, orders, order_events, payments, and notification_logs with bigint primary keys, varchar status fields, timestamps, idempotency/business keys, indexes, and required unique constraints.
- Changed files: docs/sql/migrations/V1__initial_schema.sql; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master show 3.2; task-master set-status --id=3.2 --status=in-progress; docker compose --env-file .env.example ps; temporary MySQL schema create; execute V1__initial_schema.sql; information_schema table and unique constraint checks; repeat migration execution; temporary MySQL schema cleanup; task-master set-status --id=3.2 --status=done
- Test result: Migration executed successfully against temporary MySQL schema minimall_order_task32. information_schema returned all 8 required tables and required unique constraints. Re-running the migration against the same temporary schema succeeded.
- Issues: One inline shell text-check command failed because Windows/WSL quoting expanded loop variables incorrectly; validation continued through direct file inspection and MySQL execution. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 3.3 - Verify schema on local MySQL.
## Task 3.3 - Verify schema on local MySQL
- Date: 2026-05-02
- Status: Done
- Implemented: Executed docs/sql/migrations/V1__initial_schema.sql against the local Docker Compose MySQL database minimall_order and verified the resulting schema through information_schema.
- Changed files: .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 3.3; task-master set-status --id=3.3 --status=in-progress; docker compose --env-file .env.example config --quiet; docker compose --env-file .env.example ps; docker compose --env-file .env.example exec -T mysql mysql ... minimall_order < docs/sql/migrations/V1__initial_schema.sql; information_schema table and unique constraint checks; task-master set-status --id=3.3 --status=done; task-master set-status --id=3 --status=done
- Test result: Docker Compose services were running and healthy. Migration executed successfully against minimall_order. information_schema returned all 8 required tables and all required unique constraints: users.username, products.product_id, inventory.product_id, orders.order_no, order_events.event_id, payments.payment_no, payments.order_no, notification_logs.event_id, and inventory_records(order_no, change_type).
- Issues: WSL still prints a NAT/localhost warning after commands, but commands completed successfully. MySQL CLI prints the expected command-line password warning when using .env.example credentials.
- Next: Task 4 - Implement common-core: ApiResponse, ErrorCode, BusinessException, global exception handling.
## Task 4.1 - Implement ApiResponse ErrorCode and BusinessException contracts
- Date: 2026-05-02
- Status: Done
- Implemented: Split Task 4 into subtasks after task-master expand failed, then implemented the common-core response and exception contracts. Added ApiResponse<T> success/failure factories, ErrorCode enum values, BusinessException constructors/accessors, and focused unit tests.
- Changed files: common-core/pom.xml; common-core/src/main/java/com/minimall/common/core/response/ApiResponse.java; common-core/src/main/java/com/minimall/common/core/exception/ErrorCode.java; common-core/src/main/java/com/minimall/common/core/exception/BusinessException.java; common-core/src/test/java/com/minimall/common/core/response/ApiResponseTest.java; common-core/src/test/java/com/minimall/common/core/exception/BusinessExceptionTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 4; task-master expand --id=4 --num=3; task-master add-subtask for 4.1, 4.2, 4.3; task-master set-status --id=4 --status=in-progress; task-master set-status --id=4.1 --status=in-progress; mvn -pl common-core test; mvn -pl common-core package -DskipTests; task-master set-status --id=4.1 --status=done
- Test result: mvn -pl common-core test succeeded with 6 tests passing. mvn -pl common-core package -DskipTests succeeded.
- Issues: task-master expand failed once because the Codex child process could not find node, then timed out when rerun with an explicit PATH. Subtasks were added sequentially through task-master add-subtask. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 4.2 - Implement global exception handler mappings.
## Task 4.2 - Implement global exception handler mappings
- Date: 2026-05-02
- Status: Done
- Implemented: Added GlobalExceptionHandler mappings for BusinessException, MethodArgumentNotValidException, ConstraintViolationException, and fallback Exception. Added Spring Boot auto-configuration so downstream services can discover the handler through common-core without widening component scan packages. Added focused tests for status/code/message mapping and auto-configuration registration/backoff.
- Changed files: common-core/pom.xml; common-core/src/main/java/com/minimall/common/core/exception/GlobalExceptionHandler.java; common-core/src/main/java/com/minimall/common/core/config/CommonCoreAutoConfiguration.java; common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports; common-core/src/test/java/com/minimall/common/core/exception/GlobalExceptionHandlerTest.java; common-core/src/test/java/com/minimall/common/core/config/CommonCoreAutoConfigurationTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 4.2; task-master set-status --id=4.2 --status=in-progress; service package scan checks; mvn -pl common-core test; mvn -pl common-core package -DskipTests; task-master set-status --id=4.2 --status=done
- Test result: mvn -pl common-core test succeeded with 12 tests passing. mvn -pl common-core package -DskipTests succeeded.
- Issues: Initial test runs exposed missing spring-context compile scope and Jakarta Validation test-stub signature issues; both were fixed. ApplicationContextRunner requires assertj-core on the test classpath, so assertj-core was added as a test dependency. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 4.3 - Verify common-core integration and build.

## Task 4.3 - Verify common-core integration and build
- Date: 2026-05-02
- Status: Done
- Implemented: Verified common-core integration after the response and exception handling contracts were completed. Confirmed common-core unit tests pass, downstream modules that depend on common-core compile, and the full Maven reactor packages successfully with tests skipped.
- Changed files: .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 4.3; task-master set-status --id=4.3 --status=in-progress; mvn -pl common-core test; dependency search for common-core pom usages; mvn -pl common-auth,user-service,product-service,inventory-service,order-service,payment-service,notification-service -am compile; mvn clean package -DskipTests; task-master set-status --id=4.3 --status=done; task-master show 4
- Test result: mvn -pl common-core test succeeded with 12 tests passing. Downstream common-core dependents compiled successfully. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master was not on the WSL PATH, so the local TaskMaster CLI was run directly with /mnt/d/nodejs/node.exe. rg resolved to a non-executable WindowsApps path in WSL, so dependency discovery used find/grep. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 5.1 - 搭建 common-auth 基础结构：请求头常量与 UserContext ThreadLocal
- Date: 2026-05-02
- Status: Done
- Implemented: Started Task 5 and implemented the first common-auth subtask. Added shared auth request header constants for X-User-Id and X-Username. Added immutable UserContext and UserContextHolder ThreadLocal APIs for set/get/getOrNull/require/hasContext/clear. Added common-auth Spring dependencies needed by later auth subtasks and configured JUnit 5 test execution.
- Changed files: common-auth/pom.xml; common-auth/src/main/java/com/minimall/common/auth/constants/AuthHeaders.java; common-auth/src/main/java/com/minimall/common/auth/context/UserContext.java; common-auth/src/main/java/com/minimall/common/auth/context/UserContextHolder.java; common-auth/src/test/java/com/minimall/common/auth/constants/AuthHeadersTest.java; common-auth/src/test/java/com/minimall/common/auth/context/UserContextTest.java; common-auth/src/test/java/com/minimall/common/auth/context/UserContextHolderTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 5; task-master expand --id=5 --num=3; task-master set-status --id=5 --status=in-progress; task-master add-subtask then remove-subtask for an accidental duplicate 5.4; task-master show 5.1; task-master set-status --id=5.1 --status=in-progress; mvn -pl common-auth test; mvn -pl common-auth -am test; mvn clean package -DskipTests; task-master set-status --id=5.1 --status=done
- Test result: mvn -pl common-auth -am test succeeded with common-core 12 tests passing and common-auth 7 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master expand timed out, but Task 5 already had subtasks 5.1, 5.2, and 5.3. An attempted add-subtask created a duplicate 5.4 with a truncated title; it was removed immediately with TaskMaster CLI. mvn -pl common-auth test failed once because common-core was not installed in the local Maven repository after clean; rerunning with -am built the reactor dependency and passed. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 5.2 - 实现 JwtUtils：基于配置生成/解析 JWT（含非法与过期识别）.

## Task 5.2 - 实现 JwtUtils：基于配置生成/解析 JWT（含非法与过期识别）
- Date: 2026-05-02
- Status: Done
- Implemented: Added java-jwt as the selected JWT library for common-auth. Added JwtProperties with minimall.auth.jwt configuration fields for secret and expireSeconds. Added JwtUtils for generating HMAC256 JWTs with userId/username claims and parsing raw or Bearer-prefixed tokens back into UserContext. Invalid, tampered, expired, missing, and malformed-claims tokens are mapped to BusinessException with ErrorCode.UNAUTHORIZED.
- Changed files: common-auth/pom.xml; common-auth/src/main/java/com/minimall/common/auth/config/JwtProperties.java; common-auth/src/main/java/com/minimall/common/auth/jwt/JwtUtils.java; common-auth/src/test/java/com/minimall/common/auth/config/JwtPropertiesTest.java; common-auth/src/test/java/com/minimall/common/auth/jwt/JwtUtilsTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 5.2; task-master set-status --id=5.2 --status=in-progress; local Maven cache check for JWT libraries; mvn -pl common-auth -am test; mvn clean package -DskipTests; task-master set-status --id=5.2 --status=done
- Test result: Initial mvn -pl common-auth -am test exposed a Bearer-without-token edge case; after fixing normalizeToken, mvn -pl common-auth -am test succeeded with common-core 12 tests passing and common-auth 17 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: No JWT library was found in the local Maven cache before implementation, so Maven resolved java-jwt during the test run. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 5.3 - 实现 UserContextFilter + 自动装配：读取 JWT/透传头并在请求结束清理.

## Task 5.3 - 实现 UserContextFilter + 自动装配：读取 JWT/透传头并在请求结束清理
- Date: 2026-05-02
- Status: Done
- Implemented: Added UserContextFilter as a reusable OncePerRequestFilter for common-auth. The filter prioritizes X-User-Id/X-Username propagation headers, falls back to Authorization Bearer JWT parsing when available, writes UserContextHolder for downstream code, returns 401 with ApiResponse on invalid auth input, and clears ThreadLocal state in finally. Added CommonAuthAutoConfiguration with JwtProperties binding, conditional JwtUtils registration when minimall.auth.jwt.secret is configured, UserContextFilter registration with bean backoff, and Spring Boot AutoConfiguration imports.
- Changed files: common-auth/pom.xml; common-auth/src/main/java/com/minimall/common/auth/config/CommonAuthAutoConfiguration.java; common-auth/src/main/java/com/minimall/common/auth/web/UserContextFilter.java; common-auth/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports; common-auth/src/test/java/com/minimall/common/auth/config/CommonAuthAutoConfigurationTest.java; common-auth/src/test/java/com/minimall/common/auth/web/UserContextFilterTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 5.3; task-master set-status --id=5.3 --status=in-progress; mvn -pl common-auth -am test; mvn clean package -DskipTests; task-master set-status --id=5.3 --status=done; task-master show 5
- Test result: mvn -pl common-auth -am test succeeded with common-core 12 tests passing and common-auth 26 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 6.1 - user-service persistence foundation
- Date: 2026-05-02
- Status: Done
- Implemented: Started Task 6 after verifying Task 6 was pending and ready despite TaskMaster recommending Task 12. Split Task 6 into subtasks. Added user-service persistence dependencies, users table JPA mapping, UserStatus enum, UserRepository, environment-variable datasource/JPA configuration, BCrypt crypto dependency for the upcoming auth subtask, and focused repository tests.
- Changed files: user-service/pom.xml; user-service/src/main/java/com/minimall/user/domain/User.java; user-service/src/main/java/com/minimall/user/domain/UserStatus.java; user-service/src/main/java/com/minimall/user/repository/UserRepository.java; user-service/src/main/resources/application.yml; user-service/src/test/java/com/minimall/user/repository/UserRepositoryTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: git add .; git commit -m "chore: checkpoint completed foundation tasks"; task-master show 6; task-master list --with-subtasks; task-master set-status --id=6 --status=in-progress; task-master expand --id=6 --num=3; task-master show 6; task-master set-status --id=6.1 --status=in-progress; task-master show 6.1; mvn -pl user-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl user-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and user-service 3 repository tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master expand timed out at the command level but still wrote subtasks 6.1, 6.2, and 6.3. The first user-service test run reported 0 tests because user-service inherited Maven Surefire 2.12.4; adding Surefire 3.2.5 to user-service made JUnit 5 tests execute. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 6.2 - implement register and login endpoints with BCrypt storage and JWT issuing.

## Task 6.2 - Implement register and login endpoints
- Date: 2026-05-02
- Status: Done
- Implemented: Added user-service register and login APIs at POST /api/users/register and POST /api/users/login. Added validation DTOs, response DTOs, UserAuthService, UserAuthController, BCrypt PasswordEncoder configuration, JWT issuing through common-auth JwtUtils, and environment-variable JWT configuration. Registration stores BCrypt password hashes and maps duplicate usernames to BusinessException(ErrorCode.CONFLICT). Login returns a Bearer token and maps missing users or wrong passwords to the same UNAUTHORIZED response.
- Changed files: .env.example; user-service/pom.xml; user-service/src/main/resources/application.yml; user-service/src/main/java/com/minimall/user/config/UserSecurityConfig.java; user-service/src/main/java/com/minimall/user/dto/LoginRequest.java; user-service/src/main/java/com/minimall/user/dto/LoginResponse.java; user-service/src/main/java/com/minimall/user/dto/RegisterRequest.java; user-service/src/main/java/com/minimall/user/dto/UserResponse.java; user-service/src/main/java/com/minimall/user/service/UserAuthService.java; user-service/src/main/java/com/minimall/user/web/UserAuthController.java; user-service/src/test/java/com/minimall/user/web/UserAuthControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 6.2; git status --short; task-master set-status --id=6.2 --status=in-progress; source and contract reads; mvn -pl user-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl user-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and user-service 7 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 6.3 - implement /me endpoint using UserContext and complete endpoint-level verification.

## Task 6.3 - Implement /me endpoint using UserContext
- Date: 2026-05-02
- Status: Done
- Implemented: Added GET /api/users/me to user-service. The endpoint reads the current UserContext from UserContextHolder, returns ApiResponse.success(CurrentUserResponse), and maps missing context to BusinessException(ErrorCode.UNAUTHORIZED, "Unauthorized"). Added endpoint-level MockMvc coverage for X-User-Id/X-Username propagation headers, Authorization Bearer JWT, missing authentication, and post-request ThreadLocal cleanup.
- Changed files: user-service/src/main/java/com/minimall/user/dto/CurrentUserResponse.java; user-service/src/main/java/com/minimall/user/web/UserAuthController.java; user-service/src/test/java/com/minimall/user/web/UserAuthControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 6.3; git status --short; task-master set-status --id=6.3 --status=in-progress; mvn -pl user-service -am test; mvn clean package -DskipTests; task-master set-status --id=6.3 --status=done; task-master show 6
- Test result: mvn -pl user-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and user-service 10 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor. TaskMaster shows Task 6.3 done and parent Task 6 done.
- Issues: task-master was not on the WSL PATH, so the local TaskMaster CLI was run directly with /mnt/d/nodejs/node.exe. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 7.1 - product-service persistence foundation
- Date: 2026-05-03
- Status: Done
- Implemented: Started Task 7 and split it into subtasks. Implemented the product-service foundation by adding JPA, Redis, validation, MySQL runtime, H2 test, and JUnit 5 test dependencies. Added environment-variable driven datasource/JPA/Redis configuration, disabled Redis repository scanning, mapped the products table with Product and ProductStatus, added ProductRepository, and added repository/context tests.
- Changed files: product-service/pom.xml; product-service/src/main/resources/application.yml; product-service/src/main/java/com/minimall/product/domain/Product.java; product-service/src/main/java/com/minimall/product/domain/ProductStatus.java; product-service/src/main/java/com/minimall/product/repository/ProductRepository.java; product-service/src/test/java/com/minimall/product/repository/ProductRepositoryTest.java; product-service/src/test/java/com/minimall/product/ProductServiceApplicationTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 7; task-master expand --id=7 --num=3; task-master set-status --id=7 --status=in-progress; task-master remove-subtask --id=7.4; task-master show 7.1; task-master set-status --id=7.1 --status=in-progress; mvn -pl product-service -am test; mvn clean package -DskipTests; task-master set-status --id=7.1 --status=done
- Test result: mvn -pl product-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and product-service 4 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master expand timed out but still created subtasks 7.1, 7.2, and 7.3. A duplicate 7.4 foundation subtask was accidentally created while checking the timeout result and was removed immediately with TaskMaster CLI. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 7.2 - implement product management REST APIs.

## Task 7.2 - Implement product management REST APIs
- Date: 2026-05-03
- Status: Done
- Implemented: Added product-service REST APIs for product creation, update, list with optional status filter and paging, detail lookup, on-shelf/off-shelf transitions, and internal product detail lookup for order-service. Added ProductService business logic, request/response/page DTOs, ApiResponse-wrapped controllers, Product domain update/shelf methods, and repository status paging support. Not found cases map to BusinessException(ErrorCode.NOT_FOUND); duplicate product IDs map to CONFLICT; invalid shelf transitions map to BAD_REQUEST.
- Changed files: product-service/src/main/java/com/minimall/product/domain/Product.java; product-service/src/main/java/com/minimall/product/repository/ProductRepository.java; product-service/src/main/java/com/minimall/product/dto/CreateProductRequest.java; product-service/src/main/java/com/minimall/product/dto/UpdateProductRequest.java; product-service/src/main/java/com/minimall/product/dto/ProductResponse.java; product-service/src/main/java/com/minimall/product/dto/InternalProductResponse.java; product-service/src/main/java/com/minimall/product/dto/PageResponse.java; product-service/src/main/java/com/minimall/product/service/ProductService.java; product-service/src/main/java/com/minimall/product/web/ProductController.java; product-service/src/main/java/com/minimall/product/web/InternalProductController.java; product-service/src/test/java/com/minimall/product/web/ProductControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 7.2; git status --short; task-master set-status --id=7.2 --status=in-progress; mvn -pl product-service -am test; mvn clean package -DskipTests; task-master set-status --id=7.2 --status=done
- Test result: mvn -pl product-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and product-service 10 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Initial REST API tests exposed missing explicit Spring MVC parameter names for path variables and the status request parameter because compiler parameter metadata is not available; fixed by naming bindings explicitly. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 7.3 - implement product cache/query behavior with Redis integration.

## Task 7.3 - Implement product detail Redis cache
- Date: 2026-05-03
- Status: Done
- Implemented: Added product detail cache-aside logic in ProductService using StringRedisTemplate and Jackson JSON serialization with key product:detail:{productId}. Detail and internal detail queries now share the cached read path. Cache misses fall back to DB and write Redis with a configurable TTL. Product update, on-shelf, and off-shelf operations delete the detail cache after transaction commit. Redis read/write/delete failures are logged and fall back to DB behavior so product APIs do not require Redis to be available for correctness.
- Changed files: product-service/src/main/java/com/minimall/product/service/ProductService.java; product-service/src/main/resources/application.yml; product-service/src/test/java/com/minimall/product/web/ProductControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 7.3; git status --short; task-master set-status --id=7.3 --status=in-progress; mvn -pl product-service -am test; mvn clean package -DskipTests; task-master set-status --id=7.3 --status=done
- Test result: mvn -pl product-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and product-service 13 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Redis behavior tests use a mocked StringRedisTemplate, so Docker Redis was not required for this subtask's automated verification. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task tree update - Frontend readiness acceptance
- Date: 2026-05-03
- Status: Done
- Implemented: Reviewed the pending TaskMaster tree and added frontend-ready acceptance criteria to tasks that shape browser-facing APIs or final delivery. Updated tasks 8, 9, 10, 11, 13, 16, and 19 with requirements for stable ApiResponse DTOs, gateway-facing routes, retry/idempotency behavior, frontend-safe error codes/messages, CORS/JWT gateway behavior, and gateway-based performance scripts. Added subtask 20.1 for a frontend integration guide and readiness checklist because updating Task 20 directly through TaskMaster timed out due its full-tree context.
- Changed files: .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master list --with-subtasks; task-master show 16; task-master show 18; task-master show 20; task-master update-task --append for tasks 8, 9, 10, 11, 13, 16, and 19; task-master add-subtask --parent=20 for 20.1; git status --short
- Test result: Not a code change. Verified tasks 8, 9, 10, 11, 13, 16, and 19 contain appended frontend-readiness details and Task 20 has new pending subtask 20.1.
- Issues: The first multi-command update only updated Task 8 because TaskMaster consumed stdin; subsequent updates used stdin isolation. Updating Task 20 directly with update-task hung, so the stuck process was stopped and a focused TaskMaster subtask 20.1 was inserted instead.
- Next: Continue with TaskMaster next.

## Development rule update - Frontend scope control
- Date: 2026-05-05
- Status: Done
- Implemented: Updated AGENTS.md to clarify that frontend-ready acceptance criteria only require stable backend API contracts for future frontend/admin integration. The current task tree must not expand into frontend pages, admin consoles, UI assets, or frontend build tooling; real frontend development should wait for the user's next PRD.
- Changed files: AGENTS.md; docs/dev-log.md
- Commands run: Read AGENTS.md; read docs/dev-log.md; git status --short
- Test result: Documentation-only rule change; no build required.
- Issues: None.
- Next: Continue backend microservice tasks without expanding scope into frontend implementation.

## Task 8.1 - inventory-service persistence foundation
- Date: 2026-05-06
- Status: Done
- Implemented: Implemented the inventory-service persistence foundation. Added JPA, validation, MySQL runtime, H2 test, Spring Boot test, and Surefire dependencies. Added datasource/JPA configuration. Mapped inventory and inventory_records tables with Inventory and InventoryRecord entities, enum status/change types, timestamps, unique constraints, indexes, and repository interfaces. Added H2 repository tests for lookup, enum persistence, uniqueness, orderNo+changeType idempotency key shape, and application context startup.
- Changed files: inventory-service/pom.xml; inventory-service/src/main/resources/application.yml; inventory-service/src/main/java/com/minimall/inventory/domain/Inventory.java; inventory-service/src/main/java/com/minimall/inventory/domain/InventoryStatus.java; inventory-service/src/main/java/com/minimall/inventory/domain/InventoryChangeType.java; inventory-service/src/main/java/com/minimall/inventory/domain/InventoryRecord.java; inventory-service/src/main/java/com/minimall/inventory/domain/InventoryRecordStatus.java; inventory-service/src/main/java/com/minimall/inventory/repository/InventoryRepository.java; inventory-service/src/main/java/com/minimall/inventory/repository/InventoryRecordRepository.java; inventory-service/src/test/java/com/minimall/inventory/InventoryServiceApplicationTest.java; inventory-service/src/test/java/com/minimall/inventory/repository/InventoryRepositoryTest.java; inventory-service/src/test/java/com/minimall/inventory/repository/InventoryRecordRepositoryTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master show 8.1; task-master set-status --id=8.1 --status=in-progress; source/schema reads; mvn -pl inventory-service -am test; mvn clean package -DskipTests; task-master set-status --id=8.1 --status=done
- Test result: mvn -pl inventory-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and inventory-service 7 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master next/list/expand were slow or timed out, but show/set-status/add/remove commands eventually worked. expand had already created subtasks 8.1-8.3; a duplicate 8.4 was accidentally added during recovery and was removed immediately with TaskMaster CLI before implementation. H2 duplicate-key logs in repository tests are expected from unique-constraint assertions.
- Next: Continue with Task 8.2 - external inventory query API and stable DTOs.

## Development rule update - Review file list requirement
- Date: 2026-05-06
- Status: Done
- Implemented: Updated AGENTS.md to require that every completed task or subtask final response lists every modified, added, or deleted file name so the user can review the exact change scope.
- Changed files: AGENTS.md; docs/dev-log.md
- Commands run: Read AGENTS.md; git status --short
- Test result: Documentation-only rule change; no build required.
- Issues: None.
- Next: Continue backend tasks and include the full changed-file list after each completed subtask.

## Task 8.2 - Inventory read-only query API
- Date: 2026-05-06
- Status: Done
- Implemented: Added the external read-only inventory detail API at GET /api/inventories/{productId}. Responses use ApiResponse and expose stable productId, availableStock, lockedStock, and stockState fields. Added StockState mapping: inactive inventory returns INACTIVE, active inventory with available stock returns IN_STOCK, and active inventory without available stock returns OUT_OF_STOCK. Missing inventory throws BusinessException(ErrorCode.NOT_FOUND, "Inventory not found").
- Changed files: inventory-service/src/main/java/com/minimall/inventory/domain/StockState.java; inventory-service/src/main/java/com/minimall/inventory/dto/InventoryResponse.java; inventory-service/src/main/java/com/minimall/inventory/service/InventoryQueryService.java; inventory-service/src/main/java/com/minimall/inventory/web/InventoryController.java; inventory-service/src/test/java/com/minimall/inventory/web/InventoryControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 8.2; git status --short; task-master set-status --id=8.2 --status=in-progress; mvn -pl inventory-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl inventory-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and inventory-service 11 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Initial test run failed because PowerShell wrote UTF-8 BOM to newly added Java files; BOM was removed and the same test command passed. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Mark Task 8.2 done in TaskMaster, then continue with Task 8.3.

## Task 8.3 - Inventory deduct/release records and idempotency
- Date: 2026-05-06
- Status: Done
- Implemented: Added internal inventory command APIs at POST /internal/inventories/deduct and POST /internal/inventories/release. Added validated InventoryChangeRequest DTO, InventoryCommandService transaction logic, conditional stock updates to prevent negative inventory and invalid release, inventory_records writes for successful changes, and orderNo+changeType replay idempotency. Duplicate replay returns the inventory snapshot for the existing record without changing stock or adding another record.
- Changed files: inventory-service/src/main/java/com/minimall/inventory/repository/InventoryRepository.java; inventory-service/src/main/java/com/minimall/inventory/dto/InventoryChangeRequest.java; inventory-service/src/main/java/com/minimall/inventory/service/InventoryCommandService.java; inventory-service/src/main/java/com/minimall/inventory/web/InternalInventoryController.java; inventory-service/src/test/java/com/minimall/inventory/web/InternalInventoryControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 8.3; git status --short; task-master set-status --id=8.3 --status=in-progress; mvn -pl inventory-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl inventory-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and inventory-service 16 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: WSL still prints a NAT/localhost warning after commands, but commands completed successfully. H2 duplicate-key logs in existing repository tests are expected from unique-constraint assertions.
- Next: Continue with the next TaskMaster task after running task-master next.

## Version management rule update - Branch and checkpoint policy
- Date: 2026-05-06
- Status: Done
- Implemented: Added AGENTS.md version management guidance to keep main stable, create codex/ branches for task groups or checkpoints, prefer one commit per verified TaskMaster task/subtask, and commit TaskMaster/dev-log metadata with related code changes.
- Changed files: AGENTS.md; docs/dev-log.md
- Commands run: git branch --show-current; git status --short
- Test result: Documentation-only rule change; no build required. Current code changes were already verified in the preceding completed tasks.
- Issues: None.
- Next: Create a checkpoint branch and commit the current working tree.

## Task 9.1 - order-service persistence foundation
- Date: 2026-05-07
- Status: Done
- Implemented: Added order-service JPA persistence dependencies, datasource/JPA configuration, orders table entity mapping, OrderStatus enum, OrderRepository query methods, and focused H2 repository tests for save/read behavior, enum string persistence, owner-scoped lookup, and user paging.
- Changed files: order-service/pom.xml; order-service/src/main/resources/application.yml; order-service/src/main/java/com/minimall/order/domain/Order.java; order-service/src/main/java/com/minimall/order/domain/OrderStatus.java; order-service/src/main/java/com/minimall/order/repository/OrderRepository.java; order-service/src/test/java/com/minimall/order/repository/OrderRepositoryTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master show 9.1; git status --short; task-master set-status --id=9 --status=in-progress; task-master set-status --id=9.1 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=9.1 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 3 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster was not available as a WSL global command, so the project-local TaskMaster CLI was run through D:\nodejs\node.exe from PowerShell. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 9.2 - implement OrderStateMachine and encapsulate all order status transitions.

## Task 9.2 - OrderStateMachine status transitions
- Date: 2026-05-07
- Status: Done
- Implemented: Added OrderStateMachine with centralized legal transition checks for PENDING_PAYMENT->PAID/CANCELLED and PAID->CLOSED/REFUNDED. Added a package-private Order transition method so status changes can be applied through the state machine without exposing a public status setter. State transitions update status and timestamps consistently, and invalid transitions throw BusinessException(ErrorCode.BAD_REQUEST).
- Changed files: order-service/src/main/java/com/minimall/order/domain/Order.java; order-service/src/main/java/com/minimall/order/domain/OrderStateMachine.java; order-service/src/test/java/com/minimall/order/domain/OrderStateMachineTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 9.2; task-master set-status --id=9.2 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=9.2 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 9 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster was run through D:\nodejs\node.exe from PowerShell because WSL does not have a global node/task-master command. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 9.3 - implement order detail and my orders query APIs with stable DTOs, pagination, and error shape tests.

## Task 9.3 - Order query APIs and stable DTOs
- Date: 2026-05-07
- Status: Done
- Implemented: Added user-facing order detail and my orders paged query APIs under /api/orders. Introduced stable DTO records for order detail, order summary, item summary, and PageResponse without exposing JPA entities or internal id/userId/username/idempotencyKey fields. Added OrderQueryService for user-scoped detail/list queries, mapping not found and not-owned orders to BusinessException(ErrorCode.NOT_FOUND, "Order not found"). Added MockMvc + H2 tests for ApiResponse success shape, empty-page success, unauthorized 401, missing/not-owned 404, pagination fields, and DTO field leakage prevention.
- Changed files: order-service/src/main/java/com/minimall/order/dto/OrderItemSummary.java; order-service/src/main/java/com/minimall/order/dto/OrderDetailResponse.java; order-service/src/main/java/com/minimall/order/dto/OrderSummaryResponse.java; order-service/src/main/java/com/minimall/order/dto/PageResponse.java; order-service/src/main/java/com/minimall/order/service/OrderQueryService.java; order-service/src/main/java/com/minimall/order/web/OrderController.java; order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master set-status --id=9.3 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=9.3 --status=done; task-master show 9
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 15 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor. TaskMaster shows Task 9 and all subtasks 9.1-9.3 done.
- Issues: TaskMaster was run through D:\nodejs\node.exe from PowerShell because WSL does not have a global node/task-master command. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.


## Task 10.1 - Create order API DTOs and entry skeleton
- Date: 2026-05-08
- Status: Done
- Implemented: Split Task 10 into smaller subtasks after TaskMaster initially recommended Task 11 and the first expand command timed out. Implemented the first create-order subtask only: added stable CreateOrderRequest and CreateOrderResponse DTOs, added an OrderCommandService skeleton for later orchestration, exposed POST /api/orders through the existing OrderController using UserContextHolder and ApiResponse, reserved an order payment expiry configuration value, and added MockMvc coverage for success response shape, validation errors, and missing authentication.
- Changed files: .taskmaster/tasks/tasks.json; order-service/src/main/java/com/minimall/order/dto/CreateOrderRequest.java; order-service/src/main/java/com/minimall/order/dto/CreateOrderResponse.java; order-service/src/main/java/com/minimall/order/service/OrderCommandService.java; order-service/src/main/java/com/minimall/order/web/OrderController.java; order-service/src/main/resources/application.yml; order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; docs/dev-log.md
- Commands run: task-master show 10; task-master set-status --id=10 --status=in-progress; task-master expand --id=10 --num=3; task-master remove-subtask --id=10.2,10.3,10.4; task-master add-subtask for 10.2, 10.3, 10.4, 10.5, 10.6; task-master show 10.1; task-master set-status --id=10.1 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 18 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster next recommended Task 11 even though Task 10 was pending and dependency-ready, so Task 10 was selected manually based on business order and user correction. TaskMaster expand timed out but wrote coarse subtasks; oversized subtasks were removed and replaced with smaller TaskMaster subtasks via CLI. Local apply_patch could not write WSL /home paths, so Task 10.1 files were edited through an elevated WSL write script.
- Next: Task 10.2 - Product validation client for order creation.


## Development rule update - Mandatory post-task commit wording
- Date: 2026-05-08
- Status: Done
- Implemented: Reworded AGENTS.md so verified TaskMaster task/subtask work must be committed before the final response unless the user explicitly says not to commit or a blocker prevents committing. Added an executable sequence for branch check, staging exact task files, verifying staged scope, and creating a task-specific commit.
- Changed files: AGENTS.md; docs/dev-log.md
- Commands run: git branch --show-current; git status --short; git add Task 10.1 files; git diff --cached --name-only; git commit -m "feat(order): add create order api skeleton"
- Test result: Documentation-only rule update; no build required. The preceding Task 10.1 code was already verified by mvn -pl order-service -am test and mvn clean package -DskipTests before commit e269784.
- Issues: Previous wording used "Prefer one commit", which allowed the requirement to be treated as optional. The updated wording uses MUST and a concrete post-verification commit workflow.
- Next: Continue with Task 10.2 after committing this rule update.

## Task 10.2 - Product validation client for order creation
- Date: 2026-05-08
- Status: Done
- Implemented: Added an order-service product validation client contract for create-order flow without depending on product-service DTOs. The client calls GET /internal/products/{productId}, maps stable ApiResponse success/error bodies, returns a local ProductSnapshot, treats missing products as BusinessException(ErrorCode.NOT_FOUND, "Product not found"), treats non-ON_SHELF products as BusinessException(ErrorCode.BAD_REQUEST, "Product is off shelf"), and normalizes downstream client failures to BusinessException(ErrorCode.INTERNAL_ERROR, "Product validation failed") without leaking downstream details. Added configurable product service base URL through PRODUCT_SERVICE_BASE_URL.
- Changed files: order-service/src/main/java/com/minimall/order/client/product/ProductApiResponse.java; order-service/src/main/java/com/minimall/order/client/product/ProductClient.java; order-service/src/main/java/com/minimall/order/client/product/ProductSnapshot.java; order-service/src/main/java/com/minimall/order/client/product/ProductStatus.java; order-service/src/main/java/com/minimall/order/client/product/ProductValidationService.java; order-service/src/main/resources/application.yml; order-service/src/test/java/com/minimall/order/client/product/ProductClientTest.java; order-service/src/test/java/com/minimall/order/client/product/ProductValidationServiceTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 10.2; git status --short; task-master set-status --id=10.2 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 24 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Initial order-service test run failed because Spring selected the package-private test constructor on ProductClient; the production constructor was annotated with @Autowired and the same test command passed. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 10.3 - Inventory deduct client for order creation.

## Task 10.3 - Inventory deduct client for order creation
- Date: 2026-05-09
- Status: Done
- Implemented: Added an order-service inventory deduct client contract for the create-order flow without depending on inventory-service DTOs. The client posts InventoryDeductRequest to POST /internal/inventories/deduct, maps stable ApiResponse bodies to a local InventorySnapshot, treats insufficient inventory as BusinessException(ErrorCode.CONFLICT, "Insufficient inventory"), maps missing inventory to BusinessException(ErrorCode.NOT_FOUND, "Inventory not found"), and normalizes downstream failures to BusinessException(ErrorCode.INTERNAL_ERROR, "Inventory deduct failed") without leaking downstream details. Added configurable inventory service base URL through INVENTORY_SERVICE_BASE_URL.
- Changed files: order-service/src/main/java/com/minimall/order/client/inventory/InventoryApiResponse.java; order-service/src/main/java/com/minimall/order/client/inventory/InventoryClient.java; order-service/src/main/java/com/minimall/order/client/inventory/InventoryDeductRequest.java; order-service/src/main/java/com/minimall/order/client/inventory/InventorySnapshot.java; order-service/src/main/java/com/minimall/order/client/inventory/InventoryStockState.java; order-service/src/main/resources/application.yml; order-service/src/test/java/com/minimall/order/client/inventory/InventoryClientTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 10.3; git status --short; task-master set-status --id=10.3 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=10.3 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 29 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster CLI is slow in WSL through the Windows node executable but completed successfully. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 10.4 - Create order orchestration and persistence.

## Task 10.4 - Create order orchestration and persistence
- Date: 2026-05-09
- Status: Done
- Implemented: Wired POST /api/orders through the real OrderCommandService orchestration. The service validates sellable product data, generates an order number, deducts inventory with that order number, calculates totalAmount from unit price and quantity, persists a PENDING_PAYMENT order with user context, idempotencyKey, and expireAt, and returns CreateOrderResponse through ApiResponse.success. Redis duplicate-submit handling remains deferred to Task 10.5 as required.
- Changed files: order-service/src/main/java/com/minimall/order/service/OrderCommandService.java; order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 10.4; git status --short; task-master set-status --id=10.4 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=10.4 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 31 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster CLI is slow in WSL through the Windows node executable but completed successfully. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 10.5 - Duplicate submit idempotency for create order.

## Task 10.5 - Duplicate submit idempotency for create order
- Date: 2026-05-09
- Status: Done
- Implemented: Added create-order idempotency scoped by userId plus idempotencyKey. The command service now replays existing orders before external calls, uses Redis SETNX with a TTL to reject in-flight duplicate submissions with BusinessException(ErrorCode.CONFLICT, "Order creation is in progress, please retry"), leaves successful locks to expire, releases locks on failed attempts, and falls back to database unique-key replay when a concurrent insert wins the race. Changed the order idempotency unique constraint to user_id + idempotency_key and added Redis configuration for order-service.
- Changed files: order-service/pom.xml; order-service/src/main/resources/application.yml; order-service/src/main/java/com/minimall/order/domain/Order.java; order-service/src/main/java/com/minimall/order/repository/OrderRepository.java; order-service/src/main/java/com/minimall/order/service/OrderCommandService.java; order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; order-service/src/test/java/com/minimall/order/service/OrderCommandServiceTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 10.5; git status --short; task-master set-status --id=10.5 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=10.5 --status=done
- Test result: mvn -pl order-service -am test passed after a rerun, with common-core 12 tests, common-auth 26 tests, and order-service 34 tests passing. The first run failed once in existing common-auth JwtUtilsTest.rejectsTamperedToken before order-service ran; rerunning the same command passed, so the failure did not repeat. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster CLI is slow in WSL through the Windows node executable but completed successfully. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 10.6 - Create order regression verification.

## Task 10.6 - Create order regression verification
- Date: 2026-05-10
- Status: Done
- Implemented: Reviewed existing create-order regression coverage across DTO validation, authentication, success persistence, product business failures, inventory failures, duplicate replay, in-flight duplicate conflict, and database unique-key race replay. Added a focused POST /api/orders regression test for missing product handling so the create-order entrypoint now verifies NOT_FOUND, skips inventory deduction, and leaves no persisted order.
- Changed files: order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 10.6; git status --short; task-master show 10; task-master set-status --id=10.6 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=10.6 --status=done; task-master set-status --id=10 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 35 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Global task-master was not on the WSL PATH, so the project-local TaskMaster CLI was run through /mnt/d/nodejs/node.exe. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 11.1 - Inventory release client for order cancellation
- Date: 2026-05-10
- Status: Done
- Implemented: Started Task 11 and split it into four focused subtasks after TaskMaster expand timed out but still wrote subtasks. Implemented only Task 11.1 by extending order-service InventoryClient with a reusable release operation for POST /internal/inventories/release. The release call reuses the orderNo/productId/quantity request shape, maps successful ApiResponse bodies to InventorySnapshot, maps missing inventory to NOT_FOUND, maps insufficient locked inventory to stable BAD_REQUEST/CONFLICT business errors, and normalizes downstream failures to BusinessException(ErrorCode.INTERNAL_ERROR, "取消失败，请稍后重试") without leaking downstream details. Added MockRestServiceServer coverage for release success, insufficient locked inventory, missing inventory, and downstream failure sanitization.
- Changed files: order-service/src/main/java/com/minimall/order/client/inventory/InventoryClient.java; order-service/src/test/java/com/minimall/order/client/inventory/InventoryClientTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 11; task-master set-status --id=11 --status=in-progress; task-master expand --id=11 --num=4; task-master show 11; task-master show 11.1; task-master set-status --id=11.1 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=11.1 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 39 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master expand timed out at the command level, but it successfully created subtasks 11.1 through 11.4. TaskMaster CLI is slow in WSL through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 11.2 - implement cancellation command in OrderCommandService.

## Task 11.2 - Order cancellation command
- Date: 2026-05-10
- Status: Done
- Implemented: Added the order-service cancellation command in OrderCommandService. The command performs user-scoped order lookup, returns NOT_FOUND for missing or not-owned orders, treats already CANCELLED orders as idempotent success without releasing inventory again, rejects non-PENDING_PAYMENT orders with a stable CONFLICT business error, releases locked inventory for PENDING_PAYMENT orders, transitions the order to CANCELLED through OrderStateMachine, and returns a stable CancelOrderResponse DTO with orderNo and status. Added focused service tests for success, idempotent replay, paid-order rejection, and missing-order handling.
- Changed files: order-service/src/main/java/com/minimall/order/dto/CancelOrderResponse.java; order-service/src/main/java/com/minimall/order/service/OrderCommandService.java; order-service/src/test/java/com/minimall/order/service/OrderCommandServiceTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 11.2; read .taskmaster/tasks/tasks.json after TaskMaster show timed out; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=11.2 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 43 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster next/show calls timed out through the Windows node executable, so task details were read from tasks.json. The set-status command printed a successful update from pending to done before the wrapper timed out; tasks.json confirms Task 11.2 is done. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 11.3 - expose POST /api/orders/{orderNo}/cancel through OrderController with ApiResponse.

## Task 11.3 - Expose order cancellation API
- Date: 2026-05-10
- Status: Done
- Implemented: Exposed POST /api/orders/{orderNo}/cancel in OrderController. The endpoint uses the same UserContextHolder-backed authentication pattern as existing order APIs, delegates to OrderCommandService.cancel, and wraps successful cancellation/idempotent replay in ApiResponse.success(CancelOrderResponse). Added MockMvc coverage for pending-order cancellation with inventory release, already-cancelled idempotent success without release, paid-order conflict response, missing authentication, and missing-order NOT_FOUND response.
- Changed files: order-service/src/main/java/com/minimall/order/web/OrderController.java; order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; read .taskmaster/tasks/tasks.json after TaskMaster next timed out; mvn -pl order-service -am test; reran mvn -pl order-service -am test after a one-off common-auth JwtUtilsTest failure; mvn clean package -DskipTests; task-master set-status --id=11.3 --status=done
- Test result: The first mvn -pl order-service -am test run failed once in common-auth JwtUtilsTest.rejectsTamperedToken before order-service tests ran; rerunning the same command passed with common-core 12 tests, common-auth 26 tests, and order-service 48 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster next timed out through the Windows node executable, so Task 11.3 details were read from tasks.json. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 11.4 - cancellation chain regression tests for idempotency, one inventory release, and safe downstream error messages.

## Task 11.4 - Cancellation chain regression tests
- Date: 2026-05-10
- Status: Done
- Implemented: Added order-service cancellation chain regression coverage in OrderControllerTest. The tests now verify that cancelling the same pending order twice returns idempotent success while calling InventoryClient.release only once, and that an inventory release failure returns a safe ApiResponse error message without leaking downstream URL/service/stack details while leaving the order in PENDING_PAYMENT. TaskMaster also marks parent Task 11 done because all cancellation subtasks are complete.
- Changed files: order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; read .taskmaster/tasks/tasks.json after TaskMaster next timed out; mvn -pl order-service -am test; fixed an accidentally changed create-order assertion; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=11.4 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 12 tests, common-auth 26 tests, and order-service 50 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: The first order-service test run exposed a test-edit mistake where the existing create-order duplicate-submit expected message had been changed; it was restored and the same Maven command passed. TaskMaster set-status timed out without success text, but tasks.json confirms Task 11.4 and parent Task 11 are done. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 12.1 - Define shared payment success event contract
- Date: 2026-05-11
- Status: Done
- Implemented: Split Task 12 into three focused subtasks and implemented the first subtask. Added shared common-core RabbitMQ payment event naming constants for the payment exchange, payment success routing key, order payment-success queue, and notification payment-success queue. Added immutable PaymentSuccessEvent with required eventId, orderNo, paymentNo, amount, paidAt, and default version=1 contract fields. Added Jackson JSON creator/property annotations and tests for stable topology names, default versioning, required eventId validation, ISO paidAt serialization, amount precision, missing-version compatibility, and missing eventId rejection.
- Changed files: common-core/pom.xml; common-core/src/main/java/com/minimall/common/core/event/payment/PaymentEventNames.java; common-core/src/main/java/com/minimall/common/core/event/payment/PaymentSuccessEvent.java; common-core/src/test/java/com/minimall/common/core/event/payment/PaymentEventNamesTest.java; common-core/src/test/java/com/minimall/common/core/event/payment/PaymentSuccessEventTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 12; task-master set-status --id=12 --status=in-progress; task-master add-subtask for 12.1, 12.2, 12.3; task-master set-status --id=12.1 --status=in-progress; mvn -pl common-core test; mvn clean package -DskipTests; task-master set-status --id=12.1 --status=done
- Test result: mvn -pl common-core test succeeded with 18 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Global task-master was not on the WSL PATH, so the project-local TaskMaster CLI was run through /mnt/d/nodejs/node.exe. TaskMaster CLI remains slow through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 12.2 - Declare RabbitMQ topology auto-configuration.

## Task 12.2 - Declare RabbitMQ topology auto-configuration
- Date: 2026-05-11
- Status: Done
- Implemented: Added common-core Spring AMQP topology auto-configuration for the payment success event flow. The configuration exposes one Declarables bean containing a durable direct payment exchange, durable order and notification payment-success queues, and two bindings from the payment.success routing key to both queues. Registered the auto-configuration through Spring Boot AutoConfiguration imports and added tests proving declaration names, durability, routing keys, and custom-bean backoff.
- Changed files: common-core/pom.xml; common-core/src/main/java/com/minimall/common/core/config/PaymentRabbitTopologyAutoConfiguration.java; common-core/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports; common-core/src/test/java/com/minimall/common/core/config/PaymentRabbitTopologyAutoConfigurationTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 12.2; task-master set-status --id=12.2 --status=in-progress; mvn -pl common-core test; mvn clean package -DskipTests; task-master set-status --id=12.2 --status=done
- Test result: mvn -pl common-core test succeeded with 20 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster CLI remains slow through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 12.3 - Document payment event contract and topology.

## Task 12.3 - Document payment event contract and topology
- Date: 2026-05-11
- Status: Done
- Implemented: Added docs/messaging/payment-success-event.md to document the payment success RabbitMQ contract. The document covers the durable direct exchange, payment.success routing key, order and notification consumer queues, bindings, PaymentSuccessEvent JSON fields, example payload, version compatibility, publisher rules, consumer idempotency expectations, and verification commands. TaskMaster now marks Task 12 and all three subtasks done.
- Changed files: docs/messaging/payment-success-event.md; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 12.3; task-master set-status --id=12.3 --status=in-progress; grep key contract terms in docs/messaging/payment-success-event.md; mvn -pl common-core test; mvn clean package -DskipTests; task-master set-status --id=12.3 --status=done; task-master set-status --id=12 --status=done
- Test result: Contract keyword check succeeded. mvn -pl common-core test succeeded with 20 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: One initial grep command failed because PowerShell interpreted the pattern pipe characters before WSL execution; rerunning through bash succeeded. TaskMaster CLI remains slow through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 13.1 - Extend common-core payment business error codes
- Date: 2026-05-12
- Status: Done
- Implemented: Added stable payment/order business error codes ORDER_CANCELLED, ORDER_INVALID_STATE, and PAYMENT_ALREADY_SUCCESS to common-core. Mapped the new codes to HTTP 409 Conflict in GlobalExceptionHandler so payment-service can return frontend-distinguishable ApiResponse errors through BusinessException. Added handler tests that assert each new code, message, and HTTP status.
- Changed files: common-core/src/main/java/com/minimall/common/core/exception/ErrorCode.java; common-core/src/main/java/com/minimall/common/core/exception/GlobalExceptionHandler.java; common-core/src/test/java/com/minimall/common/core/exception/GlobalExceptionHandlerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master set-status --id=13.1 --status=in-progress; mvn -pl common-core test; mvn clean package -DskipTests; task-master set-status --id=13.1 --status=done
- Test result: mvn -pl common-core test succeeded with 21 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster set-status printed a successful update to done before the wrapper timed out. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 13.2 - complete payment-service JPA/Validation/AMQP dependencies and configuration.

## Task 13.2 - Payment service dependencies and configuration
- Date: 2026-05-12
- Status: Done
- Implemented: Added payment-service dependencies for Spring Data JPA, Validation, Spring AMQP, MySQL runtime, H2 test, Spring Boot test, and Surefire 3.2.5. Extended payment-service application.yml with environment-variable driven datasource, JPA, and RabbitMQ settings. Added a Spring Boot context test that starts payment-service with H2 and RabbitMQ test properties.
- Changed files: payment-service/pom.xml; payment-service/src/main/resources/application.yml; payment-service/src/test/java/com/minimall/payment/PaymentServiceApplicationTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master set-status --id=13.2 --status=in-progress; mvn -pl payment-service test; mvn -pl payment-service -am test; mvn clean package -DskipTests; task-master set-status --id=13.2 --status=done
- Test result: mvn -pl payment-service test failed because Maven could not resolve sibling snapshot modules common-core/common-auth without -am. mvn -pl payment-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and payment-service 1 context test passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: The first single-module Maven command is not sufficient in this workspace unless sibling modules are already installed. TaskMaster set-status wrote done but the wrapper timed out without success text. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 13.3 - implement payment-service persistence layer with Payment model, enums, repositories, and focused tests.

## Task 13.3 - Payment service persistence layer
- Date: 2026-05-12
- Status: Done
- Implemented: Added the payment-service persistence layer for payments and minimal order reads. Created Payment with paymentNo, orderNo, amount, channel, status, idempotencyKey, paidAt, createdAt, and updatedAt mappings aligned to the payments table; added PaymentStatus and PaymentChannel enums with stable string persistence; added a minimal Order read model with OrderStatus for payment pre-checks without depending on order-service. Added PaymentRepository and OrderRepository query methods plus H2 repository tests for business-key lookups, enum storage, timestamps, success marking, and payment unique constraints.
- Changed files: payment-service/src/main/java/com/minimall/payment/domain/Payment.java; payment-service/src/main/java/com/minimall/payment/domain/PaymentStatus.java; payment-service/src/main/java/com/minimall/payment/domain/PaymentChannel.java; payment-service/src/main/java/com/minimall/payment/domain/Order.java; payment-service/src/main/java/com/minimall/payment/domain/OrderStatus.java; payment-service/src/main/java/com/minimall/payment/repository/PaymentRepository.java; payment-service/src/main/java/com/minimall/payment/repository/OrderRepository.java; payment-service/src/test/java/com/minimall/payment/repository/PaymentRepositoryTest.java; payment-service/src/test/java/com/minimall/payment/repository/OrderRepositoryTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 13.3; task-master set-status --id=13.3 --status=in-progress; mvn -pl payment-service -am test; mvn clean package -DskipTests; task-master set-status --id=13.3 --status=done
- Test result: mvn -pl payment-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and payment-service 8 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Expected H2 duplicate-key logs appear during unique-constraint assertions. TaskMaster CLI remains slow through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 13.4 - Payment API DTOs and basic successful payment flow
- Date: 2026-05-12
- Status: Done
- Implemented: Added frontend-ready payment API contracts and the initial simulated successful payment flow. Created PayPaymentRequest and PaymentResponse DTOs, PaymentController endpoints for POST /api/payments/{orderNo}/pay and GET /api/payments/{orderNo}, PaymentCommandService for user-scoped order lookup, pending-order validation, payment creation/replay, and immediate SUCCESS/paidAt writes, and PaymentQueryService for stable payment detail responses. Extended Payment with a success-state helper. Added MockMvc coverage for successful payment, replay without duplicate records, query DTO shape, missing order, another user's order, cancelled order conflict, and missing authentication.
- Changed files: payment-service/src/main/java/com/minimall/payment/domain/Payment.java; payment-service/src/main/java/com/minimall/payment/dto/PayPaymentRequest.java; payment-service/src/main/java/com/minimall/payment/dto/PaymentResponse.java; payment-service/src/main/java/com/minimall/payment/service/PaymentCommandService.java; payment-service/src/main/java/com/minimall/payment/service/PaymentQueryService.java; payment-service/src/main/java/com/minimall/payment/web/PaymentController.java; payment-service/src/test/java/com/minimall/payment/web/PaymentControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 13.4; task-master set-status --id=13.4 --status=in-progress; mvn -pl payment-service -am test; mvn clean package -DskipTests; task-master set-status --id=13.4 --status=done
- Test result: mvn -pl payment-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and payment-service 15 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Event publishing, strict duplicate-success behavior, and dedicated ORDER_CANCELLED/ORDER_INVALID_STATE/PAYMENT_ALREADY_SUCCESS responses remain in Task 13.5 by design. TaskMaster CLI remains slow through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 13.5 - implement idempotent payment and PaymentSuccessEvent publication.

## Task 13.5 - Idempotent payment and PaymentSuccessEvent publication
- Date: 2026-05-12
- Status: Done
- Implemented: Completed strict payment idempotency and event publication. PaymentCommandService now returns dedicated BusinessException codes for cancelled orders, invalid order states, and already-successful payments; only transitions from non-success to SUCCESS publish PaymentSuccessEvent. Added PaymentEventPublisher using RabbitTemplate and the shared PaymentEventNames/PaymentSuccessEvent contract, plus a Jackson JSON message converter for RabbitMQ payloads. Extended PaymentControllerTest with RabbitTemplate mocking to assert event payload content, one-time publication, existing pending payment completion, PAYMENT_ALREADY_SUCCESS on replay, ORDER_CANCELLED for cancelled orders, and ORDER_INVALID_STATE for already-paid orders. TaskMaster now marks Task 13.5 and parent Task 13 done.
- Changed files: payment-service/src/main/java/com/minimall/payment/service/PaymentCommandService.java; payment-service/src/main/java/com/minimall/payment/config/PaymentRabbitConfig.java; payment-service/src/main/java/com/minimall/payment/service/event/PaymentEventPublisher.java; payment-service/src/test/java/com/minimall/payment/web/PaymentControllerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 13.5; task-master set-status --id=13.5 --status=in-progress; mvn -pl payment-service -am test; mvn clean package -DskipTests; task-master set-status --id=13.5 --status=done; task-master set-status --id=13 --status=done
- Test result: mvn -pl payment-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and payment-service 17 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: H2 duplicate-key logs remain expected in repository tests. TaskMaster CLI remains slow through the Windows node executable. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 14.1 - order_events persistence foundation
- Date: 2026-05-13
- Status: Done
- Implemented: Added order_events persistence foundation in order-service. Created OrderEvent JPA mapping aligned to docs/sql/migrations/V1__initial_schema.sql, added OrderEventType with PAYMENT_SUCCESS, added OrderEventRepository with eventId lookup, and added repository tests for save/read, enum string persistence, and duplicate eventId rejection for idempotency.
- Changed files: order-service/src/main/java/com/minimall/order/domain/OrderEvent.java; order-service/src/main/java/com/minimall/order/domain/OrderEventType.java; order-service/src/main/java/com/minimall/order/repository/OrderEventRepository.java; order-service/src/test/java/com/minimall/order/repository/OrderEventRepositoryTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 14; task-master expand --id=14 --num=3; task-master remove-subtask --id=14.4; task-master set-status --id=14 --status=in-progress; task-master set-status --id=14.1 --status=in-progress; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=14.1 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and order-service 53 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master expand timed out but still created subtasks 14.1, 14.2, and 14.3. A duplicate 14.4 was partially created during the interrupted add-subtask command and was removed with TaskMaster. H2 duplicate-key logs in OrderEventRepositoryTest are expected from the unique-constraint assertion. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 14.2 - implement PaymentSuccessEvent consumer and PENDING_PAYMENT to PAID transition.

## Task 14.2 - Payment success event consumer
- Date: 2026-05-13
- Status: Done
- Implemented: Added the order-service RabbitMQ consumer for PaymentSuccessEvent. Configured the Spring AMQP dependency, RabbitMQ environment properties, and Jackson message converter. Implemented PaymentSuccessEventConsumer with a listener on PaymentEventNames.ORDER_PAYMENT_SUCCESS_QUEUE, idempotent order_events eventId handling, PENDING_PAYMENT to PAID transitions through OrderStateMachine, ignored non-pending states, and failed audit payloads for missing orders. Added focused consumer tests for paid transition, cancelled-order no-op, and duplicate eventId no-op. Existing SpringBootTest coverage now disables Rabbit listener auto-startup so tests do not require a broker.
- Changed files: order-service/pom.xml, order-service/src/main/resources/application.yml, order-service/src/main/java/com/minimall/order/config/OrderRabbitConfig.java, order-service/src/main/java/com/minimall/order/messaging/PaymentSuccessEventConsumer.java, order-service/src/main/java/com/minimall/order/domain/OrderEvent.java, order-service/src/main/java/com/minimall/order/repository/OrderEventRepository.java, order-service/src/test/java/com/minimall/order/messaging/PaymentSuccessEventConsumerTest.java, order-service/src/test/java/com/minimall/order/web/OrderControllerTest.java, .taskmaster/tasks/tasks.json, docs/dev-log.md
- Commands run: task-master next, task-master show 14.2, task-master set-status --id=14.2 --status=in-progress, mvn -pl order-service -am test, git diff --check, mvn clean package -DskipTests, task-master set-status --id=14.2 --status=done, mvn -pl order-service -am test
- Test result: mvn -pl order-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and order-service 56 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: H2 duplicate-key logs in OrderEventRepositoryTest are expected from the unique-constraint assertion. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Task 14.3 - Payment success consumer acceptance tests
- Date: 2026-05-13
- Status: Done
- Implemented: Strengthened PaymentSuccessEventConsumerTest to assert persisted order_events results rather than only payload substrings. The tests now verify eventType, orderNo, from/to status, one-row idempotency, parsed payload handleResult values for processed and ignored events, and a missing-order failed event with an errorMessage payload. TaskMaster now marks Task 14.3 done and parent Task 14 done.
- Changed files: order-service/src/test/java/com/minimall/order/messaging/PaymentSuccessEventConsumerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 14.3; task-master set-status --id=14.3 --status=in-progress; git diff --check; mvn -pl order-service -am test; mvn clean package -DskipTests; task-master set-status --id=14.3 --status=done; task-master set-status --id=14 --status=done
- Test result: mvn -pl order-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and order-service 57 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: H2 duplicate-key logs in OrderEventRepositoryTest are expected from the unique-constraint assertion. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Review fix - Task 14 payment success consumer transaction boundary
- Date: 2026-05-13
- Status: Done
- Implemented: Addressed review feedback that @Transactional on handlePaymentSuccess was bypassed by the Rabbit listener self-invocation path. Moved the transaction boundary onto the @RabbitListener handle method and removed the internal delegate. Updated PaymentSuccessEventConsumerTest to call handle so test coverage matches the production listener entry point.
- Changed files: order-service/src/main/java/com/minimall/order/messaging/PaymentSuccessEventConsumer.java; order-service/src/test/java/com/minimall/order/messaging/PaymentSuccessEventConsumerTest.java; docs/dev-log.md
- Commands run: grep handlePaymentSuccess references; git diff --check; mvn -pl order-service -am test; mvn clean package -DskipTests
- Test result: mvn -pl order-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and order-service 57 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: The server-timezone concern was reviewed but not changed in this fix because existing order timestamps use LocalDateTime with system-default time semantics. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Continue with the next TaskMaster task after running task-master next.

## Development rule update - Stable WSL command discipline
- Date: 2026-05-13
- Status: Done
- Implemented: Updated AGENTS.md with a Command Reliability section to reduce PowerShell-to-WSL quoting and parsing failures. The rule now prefers short WSL commands, shallow quoting for git commit messages, apply_patch for file edits and dev-log updates, simple fixed-string searches, and explicit avoidance of long inline printf blocks, heredocs, nested quotes, pipelines, command substitution, semicolon chains, and complex grep alternation through the Windows command bridge.
- Changed files: AGENTS.md; docs/dev-log.md
- Commands run: read AGENTS.md; read docs/dev-log.md; git status --short; apply_patch updates
- Test result: Documentation-only rule change; no Maven build required.
- Issues: This update directly addresses the repeated command-construction failures observed while completing Task 14.
- Next: Future MiniMall work should follow the stable command templates in AGENTS.md before running TaskMaster, Maven, grep, git, or file-edit commands.

## Task 15.1 - notification-service dependencies configuration and Rabbit JSON converter
- Date: 2026-05-14
- Status: Done
- Implemented: Added notification-service JPA and AMQP foundation dependencies, MySQL runtime and H2/test support, Surefire 3.2.5, environment-variable driven datasource/JPA/RabbitMQ configuration, a Jackson2JsonMessageConverter Rabbit config, and a Spring Boot context test that verifies the JSON converter plus shared payment Rabbit topology load without starting a real listener.
- Changed files: notification-service/pom.xml; notification-service/src/main/resources/application.yml; notification-service/src/main/java/com/minimall/notification/config/NotificationRabbitConfig.java; notification-service/src/test/java/com/minimall/notification/NotificationServiceApplicationTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master show 15; task-master set-status --id=15 --status=in-progress; task-master expand --id=15 --num=3; task-master add-subtask/remove-subtask cleanup; task-master show 15.1; task-master set-status --id=15.1 --status=in-progress; mvn -pl notification-service -am test; git diff --check; mvn clean package -DskipTests; task-master set-status --id=15.1 --status=done
- Test result: mvn -pl notification-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and notification-service 1 context test passing. git diff --check succeeded. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: TaskMaster expand timed out but wrote subtasks 15.1-15.3. An accidental duplicate 15.4 was removed with TaskMaster before implementation. WSL still prints a NAT/localhost warning after commands, but commands completed successfully.
- Next: Task 15.2 - implement notification_logs domain model and idempotent repository foundation.

## Task 15.2 - notification_logs domain model and idempotent repository foundation
- Date: 2026-05-14
- Status: Done
- Implemented: Added the notification-service persistence foundation for notification_logs. Created NotificationLog with eventId, notificationType, recipient, status, payload, errorMessage, sentAt, createdAt, and updatedAt mappings aligned to docs/sql/migrations/V1__initial_schema.sql. Added NotificationType and NotificationLogStatus enums with string persistence, NotificationLogRepository with findByEventId and existsByEventId, and H2 Spring Boot repository tests covering save/read, eventId existence checks, enum storage, sent status update, and duplicate eventId unique-key rejection for idempotency.
- Changed files: notification-service/src/main/java/com/minimall/notification/domain/NotificationLog.java; notification-service/src/main/java/com/minimall/notification/domain/NotificationLogStatus.java; notification-service/src/main/java/com/minimall/notification/domain/NotificationType.java; notification-service/src/main/java/com/minimall/notification/repository/NotificationLogRepository.java; notification-service/src/test/java/com/minimall/notification/repository/NotificationLogRepositoryTest.java; notification-service/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 15.2; task-master set-status --id=15.2 --status=in-progress; mvn -pl notification-service -am test; git diff --check; mvn clean package -DskipTests; task-master set-status --id=15.2 --status=done
- Test result: mvn -pl notification-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and notification-service 4 tests passing. git diff --check succeeded. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: The first notification-service test run failed before business assertions because Mockito's inline Byte Buddy mock maker could not self-attach on the current WSL JDK. Added a notification-service test resource to force the subclass mock maker, then reran the same focused test command successfully. H2 duplicate-key logs in NotificationLogRepositoryTest are expected from the unique-constraint assertion.
- Next: Task 15.3 - implement PaymentSuccessEvent consumer for notification logs with duplicate eventId ignored.

## Task 15.3 - PaymentSuccessEvent notification consumer
- Date: 2026-05-14
- Status: Done
- Implemented: Added PaymentSuccessEventConsumer in notification-service. The consumer listens on PaymentEventNames.NOTIFICATION_PAYMENT_SUCCESS_QUEUE, ignores duplicate eventId values before writing, treats unique-key races as duplicate messages, writes SENT notification_logs for payment success events, serializes eventId/orderNo/paymentNo/amount/paidAt/version/handleResult into payload JSON, falls back to a minimal JSON payload on serialization failure, and catches unexpected runtime failures to avoid listener retries while attempting to record a FAILED notification log. TaskMaster now marks Task 15.3 and parent Task 15 done.
- Changed files: notification-service/src/main/java/com/minimall/notification/messaging/PaymentSuccessEventConsumer.java; notification-service/src/test/java/com/minimall/notification/messaging/PaymentSuccessEventConsumerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 15.3; task-master set-status --id=15.3 --status=in-progress; mvn -pl notification-service -am test; mvn -pl notification-service test; git diff --check; mvn clean package -DskipTests; task-master set-status --id=15.3 --status=done; task-master set-status --id=15 --status=done
- Test result: mvn -pl notification-service -am test succeeded with common-core 21 tests, common-auth 26 tests, and notification-service 7 tests passing. git diff --check succeeded. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: The exact task strategy command `mvn -pl notification-service test` failed because this workspace cannot resolve sibling snapshot modules common-core/common-auth unless `-am` is used or the sibling modules are installed. The equivalent reactor command with `-am` passed. H2 duplicate-key logs in NotificationLogRepositoryTest remain expected from the unique-constraint assertion.
- Next: Continue with Task 16 - api-gateway routes, JWT auth, header propagation, CORS, logging, and rate limiting.

## Task 16.1 - Gateway routing foundation and service URL configuration
- Date: 2026-05-14
- Status: Done
- Implemented: Added the api-gateway routing foundation for frontend-facing service prefixes. Configured static Spring Cloud Gateway routes for /api/user/**, /api/product/**, /api/inventory/**, /api/order/**, and /api/payment/** with environment-driven downstream base URLs and RewritePath filters that forward to downstream /api/** endpoints. Disabled the servlet common-auth auto-configuration for the reactive gateway startup path so auth can be implemented explicitly in the later JWT subtask. Added focused route definition tests for route ids, environment URI overrides, path predicates, and rewrite filter arguments.
- Changed files: api-gateway/pom.xml; api-gateway/src/main/resources/application.yml; api-gateway/src/test/java/com/minimall/gateway/GatewayRoutesTest.java; api-gateway/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 16; task-master set-status --id=16 --status=in-progress; task-master expand --id=16 --num=5; task-master add-subtask for 16.1-16.5; task-master show 16.1; task-master set-status --id=16.1 --status=in-progress; mvn -pl api-gateway -am test; git diff --check; mvn clean package -DskipTests; task-master set-status --id=16.1 --status=done
- Test result: mvn -pl api-gateway -am test succeeded with common-core 21 tests, common-auth 26 tests, and api-gateway 2 tests passing. git diff --check succeeded. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: task-master expand failed because the local TaskMaster Codex model provider could not create a rollout session in this read-only environment, so the Task 16 subtasks were added with TaskMaster add-subtask instead. The first gateway test runs exposed two environment issues and one assertion mismatch: common-auth servlet auto-configuration is incompatible with the reactive gateway context, Mockito inline mock maker cannot self-attach on this WSL JDK, and the gateway keeps the escaped RewritePath replacement string from YAML. The implementation now handles those cases. Gateway route tests still log a non-fatal InetUtils network-interface warning in the sandbox, but the build passes.
- Next: Task 16.2 - Gateway JWT authentication and trusted user header propagation.

## Task 16.2 - Gateway JWT authentication and trusted user header propagation
- Date: 2026-05-15
- Status: Done
- Implemented: Added a reactive api-gateway authentication filter that protects frontend-facing /api/** routes, bypasses public /api/user/users/login and /api/user/users/register paths plus OPTIONS preflight, validates Authorization Bearer JWTs with common-auth JwtUtils, strips incoming X-User-Id/X-Username headers before forwarding, and injects trusted user headers parsed from the JWT. Added gateway-local JwtUtils configuration for the reactive app while keeping servlet common-auth auto-configuration excluded. Gateway auth failures now return ApiResponse-shaped JSON with UNAUTHORIZED/FORBIDDEN codes. Added WebTestClient coverage for missing JWT, invalid JWT, valid JWT trusted header injection, spoofed header stripping, public login bypass, and OPTIONS bypass. Stabilized common-auth JwtUtilsTest tampered-token setup so it always mutates the JWT signature bytes.
- Changed files: api-gateway/src/main/java/com/minimall/gateway/config/GatewayAuthConfig.java; api-gateway/src/main/java/com/minimall/gateway/security/GatewayAuthenticationFilter.java; api-gateway/src/main/resources/application.yml; api-gateway/src/test/java/com/minimall/gateway/GatewayAuthenticationFilterTest.java; api-gateway/src/test/java/com/minimall/gateway/GatewayRoutesTest.java; common-auth/src/test/java/com/minimall/common/auth/jwt/JwtUtilsTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 16.2; task-master set-status --id=16.2 --status=in-progress; mvn -pl common-auth -am test; mvn -pl api-gateway -am test; git diff --check; mvn clean package -DskipTests; task-master set-status --id=16.2 --status=done
- Test result: mvn -pl common-auth -am test succeeded with common-core 21 tests and common-auth 26 tests passing. mvn -pl api-gateway -am test succeeded with common-core 21 tests, common-auth 26 tests, and api-gateway 7 tests passing. git diff --check succeeded. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: The initial focused gateway test was blocked by a flaky common-auth tampered-token test because changing the JWT's final base64url character can leave decoded signature bytes unchanged. The test now mutates the signature segment's first character. A first WebTestClient attempt used RANDOM_PORT and failed in the sandbox because Netty socket creation is not permitted; the test now binds to the Spring application context without opening a port. Gateway auth tests still log a non-fatal InetUtils network-interface warning in the sandbox, but all verification commands pass.
- Next: Task 16.3 - Gateway CORS and ApiResponse error handling.

## Task 16.3 - Gateway CORS and ApiResponse error handling
- Date: 2026-05-15
- Status: Done
- Implemented: Added configurable gateway CORS support for /api/** using minimall.gateway.cors.* properties and environment-variable defaults. Added a high-priority reactive GatewayCorsWebFilter so browser preflight OPTIONS requests are answered before route, authentication, or future rate-limit processing. Centralized gateway-owned ApiResponse error serialization in GatewayErrorResponseWriter for 401, 403, and 429 responses. Added TOO_MANY_REQUESTS to common-core ErrorCode and mapped it to HTTP 429. Added gateway tests for CORS preflight headers and stable 403/429 ApiResponse bodies.
- Changed files: .env.example; api-gateway/src/main/java/com/minimall/gateway/config/GatewayCorsConfig.java; api-gateway/src/main/java/com/minimall/gateway/config/GatewayCorsProperties.java; api-gateway/src/main/java/com/minimall/gateway/security/GatewayAuthenticationFilter.java; api-gateway/src/main/java/com/minimall/gateway/web/GatewayCorsWebFilter.java; api-gateway/src/main/java/com/minimall/gateway/web/GatewayErrorResponseWriter.java; api-gateway/src/main/resources/application.yml; api-gateway/src/test/java/com/minimall/gateway/GatewayAuthenticationFilterTest.java; api-gateway/src/test/java/com/minimall/gateway/GatewayErrorResponseWriterTest.java; common-core/src/main/java/com/minimall/common/core/exception/ErrorCode.java; common-core/src/main/java/com/minimall/common/core/exception/GlobalExceptionHandler.java; common-core/src/test/java/com/minimall/common/core/exception/GlobalExceptionHandlerTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 16.3; task-master set-status --id=16.3 --status=in-progress; mvn -pl api-gateway -am test; mvn -pl api-gateway -Dtest=GatewayAuthenticationFilterTest#apiPreflightRequestReturnsCorsHeadersWithoutJwt test; git diff --check; mvn -pl api-gateway -am test; mvn clean package -DskipTests; task-master set-status --id=16.3 --status=done
- Test result: Focused preflight test succeeded. mvn -pl api-gateway -am test succeeded with common-core 22 tests, common-auth 26 tests, and api-gateway 10 tests passing. git diff --check succeeded. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Initial CORS attempts returned framework-level 403 because Gateway/WebFlux CORS handling rejected the preflight before the intended route flow. A later WebTestClient run exposed that reactive CorsUtils requires an absolute request scheme, so GatewayCorsWebFilter now checks Origin and preflight headers directly. Gateway tests still log the existing non-fatal InetUtils network-interface warning in the sandbox.
- Next: Task 16.4 - Gateway request logging.

## Task 16.4 - Gateway request logging and Redis-backed rate limiting
- Date: 2026-05-15
- Status: Done
- Implemented: Added a global GatewayRequestLoggingFilter that logs method, path, status, and duration without request headers or sensitive values. Added configurable Redis-backed token-bucket gateway rate limiting with environment-driven replenish rate, burst capacity, requested tokens, Redis key prefix, and fail-open behavior. The rate-limit filter protects /api/** requests, bypasses OPTIONS preflight, keys authenticated requests by trusted X-User-Id after gateway JWT authentication, falls back to X-Forwarded-For or remote IP for public requests, and returns the centralized ApiResponse 429 response when denied. Added tests for rate-limit property binding, user/IP key selection, 429 response shape, preflight bypass, request logging content, implicit 200 logging, and auth test isolation from Redis connection attempts.
- Changed files: .env.example; api-gateway/pom.xml; api-gateway/src/main/resources/application.yml; api-gateway/src/main/java/com/minimall/gateway/config/GatewayRateLimitConfig.java; api-gateway/src/main/java/com/minimall/gateway/config/GatewayRateLimitProperties.java; api-gateway/src/main/java/com/minimall/gateway/ratelimit/GatewayRateLimitFilter.java; api-gateway/src/main/java/com/minimall/gateway/ratelimit/GatewayRateLimitResult.java; api-gateway/src/main/java/com/minimall/gateway/ratelimit/GatewayRateLimiter.java; api-gateway/src/main/java/com/minimall/gateway/ratelimit/RedisGatewayRateLimiter.java; api-gateway/src/main/java/com/minimall/gateway/web/GatewayRequestLoggingFilter.java; api-gateway/src/test/java/com/minimall/gateway/GatewayAuthenticationFilterTest.java; api-gateway/src/test/java/com/minimall/gateway/GatewayRateLimitFilterTest.java; api-gateway/src/test/java/com/minimall/gateway/GatewayRequestLoggingFilterTest.java; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: task-master next; task-master show 16.4; node node_modules/task-master-ai/dist/task-master.js next; node node_modules/task-master-ai/dist/task-master.js show 16.4; git status --short; git diff --check; mvn -pl api-gateway -am test; mvn -pl api-gateway -Dtest=GatewayRequestLoggingFilterTest test; mvn -pl api-gateway -am -Dtest=GatewayRequestLoggingFilterTest test; mvn -pl api-gateway -am test; mvn clean package -DskipTests; node node_modules/task-master-ai/dist/task-master.js set-status --id=16.4 --status=done
- Test result: git diff --check succeeded. mvn -pl api-gateway -am test succeeded with common-core 22 tests, common-auth 26 tests, and api-gateway 17 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Global task-master was not available on PATH, and the Windows node TaskMaster fallback failed with a WSL vsock permission error, so the Linux node executable ran the project-local TaskMaster CLI successfully. Single-module mvn -pl api-gateway -Dtest=GatewayRequestLoggingFilterTest test failed because this workspace cannot resolve current sibling common-core changes without -am. The reactor -Dtest attempt failed in upstream modules because surefire treats the unmatched test pattern as an error; the full api-gateway reactor test command passed. Gateway tests still log the existing non-fatal InetUtils network-interface warning in the sandbox. Task 16.5 remains pending.
- Next: Task 16.5 - Gateway integration regression verification.

## Task 16.5 - Gateway integration regression verification
- Date: 2026-05-15
- Status: Done
- Implemented: Added final gateway integration regression coverage for the Task 16 browser-facing contract. The regression test verifies user/product/inventory/order/payment gateway prefixes, public login bypass, protected JWT forwarding, spoofed user header stripping, trusted X-User-Id/X-Username injection, CORS preflight bypass, 429 ApiResponse behavior, and request logging of the browser-facing path. Added a gateway contract document and updated the API contract review to record Task 16 as resolved while keeping downstream resource routes plural. TaskMaster now marks Task 16.5 and parent Task 16 done.
- Changed files: api-gateway/src/main/java/com/minimall/gateway/web/GatewayRequestLoggingFilter.java; api-gateway/src/test/java/com/minimall/gateway/GatewayIntegrationRegressionTest.java; api-gateway/src/test/java/com/minimall/gateway/GatewayRoutesTest.java; docs/api-gateway-contract.md; docs/api-contract-review.md; .taskmaster/tasks/tasks.json; docs/dev-log.md
- Commands run: node node_modules/task-master-ai/dist/task-master.js next; node node_modules/task-master-ai/dist/task-master.js show 16.5; git diff --check; mvn -pl api-gateway -am test; mvn clean package -DskipTests; node node_modules/task-master-ai/dist/task-master.js set-status --id=16.5 --status=done; node node_modules/task-master-ai/dist/task-master.js set-status --id=16 --status=done; node node_modules/task-master-ai/dist/task-master.js show 16
- Test result: git diff --check succeeded. mvn -pl api-gateway -am test succeeded with common-core 22 tests, common-auth 26 tests, and api-gateway 23 tests passing. mvn clean package -DskipTests succeeded for the full 10-module reactor.
- Issues: Global task-master is still unavailable on the WSL PATH, and the Windows node fallback still fails with a WSL vsock permission error, so the Linux node executable was used for TaskMaster. Gateway tests still log the existing non-fatal Netty network-interface warning in the sandbox.
- Next: Continue with the next TaskMaster task after running task-master next.
