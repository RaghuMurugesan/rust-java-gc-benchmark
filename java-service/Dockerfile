FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app
COPY Service.java .
RUN javac Service.java

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/*.class .
EXPOSE 8080
# GC flags passed via environment or docker-compose
ENTRYPOINT ["java"]
CMD ["Service"]
