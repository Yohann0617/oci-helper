FROM maven:3.8.7-openjdk-18-slim AS builder

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests \
    && cp target/oci-helper-*.jar /app/oci-helper.jar

FROM eclipse-temurin:17.0.14_7-jre-alpine

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    OCI_HELPER_VERSION=1.2.4

RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

WORKDIR /app/oci-helper

COPY --from=builder /app/oci-helper.jar .

EXPOSE 8818

CMD exec java \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED \
    -jar oci-helper.jar | tee -a /var/log/oci-helper.log