FROM registry.cn-beijing.aliyuncs.com/daodao-bot/maven:3.9 AS builder
WORKDIR /build
COPY . .
RUN mkdir -p /root/.m2
RUN echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/central</url></mirror></mirrors></settings>' > /root/.m2/settings.xml
RUN mvn clean package -Dmaven.test.skip=true -U -e -B -f pom.xml

FROM registry.cn-beijing.aliyuncs.com/daodao-bot/openjdk:21
COPY --from=builder /build/target/*.jar app.jar
VOLUME /tmp
EXPOSE 80
ENTRYPOINT ["java","-jar","/app.jar"]
