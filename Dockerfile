FROM maven:3 as builder

WORKDIR /build
COPY pom.xml /build/pom.xml
RUN mvn -B package; echo ""

COPY src /build/src
RUN mvn -B package

FROM amazoncorretto:18

WORKDIR /app

COPY --from=builder /build/target/BugReporter-jar-with-dependencies.jar .

ENTRYPOINT []
CMD ["java", "-jar", "BugReporter-jar-with-dependencies.jar"]