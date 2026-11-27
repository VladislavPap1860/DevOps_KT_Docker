FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY src ./src
RUN mkdir -p out && javac -d out src/com/shop/Main.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/out ./out
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-cp", "out", "com.shop.Main"]