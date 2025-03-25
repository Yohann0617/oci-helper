FROM maven:3.8.7-openjdk-18-slim AS builder

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests \
    && cp target/oci-helper-1.1.8.jar /app/oci-helper.jar

FROM eclipse-temurin:17-jre-jammy

RUN locale-gen zh_CN.UTF-8 && \
    update-locale LANG=zh_CN.UTF-8 && \
    ln -fs /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8

WORKDIR /app/oci-helper

COPY --from=builder /app/oci-helper.jar .

EXPOSE 8818

CMD ["sh", "-c", "java --add-opens java.base/java.lang.invoke=ALL-UNNAMED -jar oci-helper.jar | tee /var/log/oci-helper.log"]
