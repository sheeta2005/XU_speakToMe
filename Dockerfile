# 10/20：构建阶段执行全模块测试，镜像只包含运行 JRE 和应用包。
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
# 10/20：官方 Java Wrapper 自行解压并校验分发包，不依赖宿主机或镜像的 unzip。
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY speakToMe-common/ speakToMe-common/
COPY speakToMe-framework/ speakToMe-framework/
COPY speakToMe-user/ speakToMe-user/
COPY speakToMe-content/ speakToMe-content/
COPY speakToMe-canteen/ speakToMe-canteen/
COPY speakToMe-admin/ speakToMe-admin/
COPY speakToMe-app/ speakToMe-app/
RUN --mount=type=cache,target=/root/.m2 chmod +x mvnw && ./mvnw -B package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app && mkdir /app/archive && chown app:app /app/archive
COPY --from=build --chown=app:app /workspace/speakToMe-app/target/speak-to-me.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
