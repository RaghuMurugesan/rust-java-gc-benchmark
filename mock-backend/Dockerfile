FROM golang:1.22-alpine AS builder
WORKDIR /app
COPY main.go .
RUN go build -o mock-backend main.go

FROM alpine:3.19
WORKDIR /app
COPY --from=builder /app/mock-backend .
EXPOSE 8080
CMD ["./mock-backend"]
