FROM maven:3.8.7-openjdk-18-slim AS builder

WORKDIR /app

COPY ../asusdemo/src/main/java/com/yohann/asusdemo/utils .

RUN mvn clean package -DskipTests \
    && cp target/oci-helper-1.1.7.jar /app/oci-helper.jar

FROM eclipse-temurin:17-jre-ubi9-minimal

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -fs /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && mkdir -p /app/oci-helper/keys && touch /app/oci-helper/oci-helper.db \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app/oci-helper

COPY --from=builder /app/oci-helper.jar .

EXPOSE 8818

CMD ["sh", "-c", "java -jar oci-helper.jar | tee /var/log/oci-helper.log"]
