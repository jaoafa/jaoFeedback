FROM maven:3 AS builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B package; echo ""

COPY src /build/src
RUN mvn -B package

FROM amazoncorretto:21.0.8

WORKDIR /app

COPY --from=builder /build/target/jaoFeedback-jar-with-dependencies.jar .

ENV FEEDBACKS_PATH=/data/feedbacks.json
ENV CONFIG_PATH=/data/config.json

ENTRYPOINT []
CMD ["java", "-jar", "jaoFeedback-jar-with-dependencies.jar"]
