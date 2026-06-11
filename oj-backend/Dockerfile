# 构建阶段
FROM m.daocloud.io/docker.io/library/maven:3.8-eclipse-temurin-17 AS builder
WORKDIR /app

# 配置 Maven 使用阿里云镜像（加速依赖下载）
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><name>Aliyun Maven</name><url>https://maven.aliyun.com/repository/central</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 先复制 pom.xml 下载依赖（利用缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源码并构建
COPY src ./src
COPY sandbox ./sandbox
RUN mvn package -DskipTests -B

# 运行阶段
FROM m.daocloud.io/docker.io/library/eclipse-temurin:17-jre
WORKDIR /app

# 安装 Docker CLI（代码沙箱需要）
RUN apt-get update && \
    apt-get install -y docker.io && \
    rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/oj-backend-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8101
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=prod"]
