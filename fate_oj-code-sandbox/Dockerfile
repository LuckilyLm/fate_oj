FROM maven:3.8.8-eclipse-temurin-8 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends git make gcc libc6-dev libcap-dev libseccomp-dev libsystemd-dev pkg-config g++ bash coreutils procps ca-certificates \
    && git clone --depth=1 https://github.com/ioi/isolate.git /tmp/isolate \
    && make -C /tmp/isolate isolate isolate-cg-keeper default.cf systemd/isolate.service \
    && make -C /tmp/isolate install \
    && sed -i 's/^subid_user = isolate/# subid_user = isolate/; s/^# first_uid = 60000/first_uid = 60000/; s/^# first_gid = 60000/first_gid = 60000/; s/^# num_boxes = 1000/num_boxes = 1000/' /usr/local/etc/isolate \
    && rm -rf /tmp/isolate \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar /app/fate-oj-code-sandbox.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8090

ENTRYPOINT ["/app/docker-entrypoint.sh"]
