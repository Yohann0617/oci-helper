FROM maven:3-eclipse-temurin-21 AS builder

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests \
    && cp target/oci-helper-*.jar /app/oci-helper.jar

FROM bellsoft/liberica-openjre-alpine:21

ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    OCI_HELPER_VERSION=3.0.4

RUN apk add --no-cache openssh lsof curl tzdata libudev-zero && \
    cp /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    mkdir -p /root/.ssh && \
    echo -e "Host *\n  HostKeyAlgorithms +ssh-rsa\n  PubkeyAcceptedKeyTypes +ssh-rsa" > /root/.ssh/config && \
    chmod 700 /root/.ssh && chmod 600 /root/.ssh/config

WORKDIR /app/oci-helper

COPY --from=builder /app/oci-helper.jar .

EXPOSE 8818

CMD exec java \
    --add-opens java.base/java.net=ALL-UNNAMED \
    --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED \
    -jar oci-helper.jar | tee -a /var/log/oci-helper.log