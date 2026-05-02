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
