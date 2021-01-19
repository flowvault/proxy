FROM flowdocker/play_builder:latest-java13 as builder
ADD . /opt/play
WORKDIR /opt/play
RUN SBT_OPTS="-Xms1024M -Xmx2048M -Xss2M -XX:MaxMetaspaceSize=2048M" sbt clean stage

FROM flowdocker/play:latest-java13
COPY --from=builder /opt/play /opt/play
WORKDIR /opt/play/target/universal/stage
ENTRYPOINT ["java", "-jar", "/root/environment-provider.jar", "--service", "play", "proxy", "bin/proxy"]
HEALTHCHECK --interval=5s --timeout=5s --retries=10 \
  CMD curl --insecure -f https://localhost:9443/_internal_/healthcheck || curl -f http://localhost:9000/_internal_/healthcheck || exit 1
