# syntax=docker/dockerfile:1

# ===== MediRAG Backend =====
# 多阶段构建：Maven 构建 + JRE 运行

FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /build

# 先拷贝 pom 利用 Docker 层缓存加速依赖下载
COPY pom.xml .
RUN mvn -B dependency:go-offline -q || true

COPY src ./src
RUN mvn -B clean package -DskipTests -q

FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 运行
RUN useradd -r -u 1001 medirag
USER medirag

COPY --from=build /build/target/medirag-backend.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
