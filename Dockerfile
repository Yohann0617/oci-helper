# 第一阶段：使用Maven构建jar
FROM maven:3.8.4-openjdk-11 AS builder

# 设置工作目录
WORKDIR /app

# 复制Maven项目描述文件和依赖清单
COPY . .

# 执行Maven构建并将构建的jar文件复制到指定目录
RUN mvn clean package -DskipTests \
    && cp target/ocihelper-0.0.1.jar /app/oci-helper.jar

# 支持AMD、ARM两种架构的镜像
FROM openjdk:8-jdk-alpine

# 安装依赖包
RUN apk update \
    && apk add --no-cache tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && mkdir -p /app/oci-helper/keys && touch /app/oci-helper/oci-helper.db

# 设置工作目录
WORKDIR /app/oci-helper

# 从第一阶段复制构建的jar文件
COPY --from=builder /app/oci-helper.jar .

# 暴露应用的端口
EXPOSE 8818

# 定义启动命令
CMD ["java", "-jar", "oci-helper.jar"]
