ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:17-jre-alpine
FROM ${JAVA_RUNTIME_IMAGE}

ARG JAR_FILE

WORKDIR /app

RUN addgroup -S app && adduser -S app -G app

COPY ${JAR_FILE} /app/app.jar

ENV JAVA_OPTS=""

EXPOSE 8080 8101 8102 8103 8104 8105 8106

USER app

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
