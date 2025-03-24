# 第一阶段：使用Maven构建jar
FROM maven:3.8.6-openjdk-11 AS builder

# 设置工作目录
WORKDIR /app

# 复制Maven项目描述文件和依赖清单
COPY . .

# 执行Maven构建并将构建的jar文件复制到指定目录
RUN mvn clean package -DskipTests \
    && cp target/oci-helper-1.1.7.jar /app/oci-helper.jar

# 支持AMD、ARM两种架构的镜像
FROM openjdk:8-jre

# 安装依赖包
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -fs /usr/share/zoneinfo/Asia/Shanghai /etc/localtime \
    && echo "Asia/Shanghai" > /etc/timezone \
    && mkdir -p /app/oci-helper/keys && touch /app/oci-helper/oci-helper.db \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /app/oci-helper

# 从第一阶段复制构建的jar文件
COPY --from=builder /app/oci-helper.jar .

# 暴露应用的端口
EXPOSE 8818

# 定义启动命令
CMD ["sh", "-c", "java -jar oci-helper.jar | tee /var/log/oci-helper.log"]
