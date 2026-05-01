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
