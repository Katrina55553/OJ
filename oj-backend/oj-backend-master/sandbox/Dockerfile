# 通用代码执行沙箱
# 构建时通过 LANGUAGE 参数选择语言环境
ARG LANGUAGE=python

# ==================== C++ ====================
FROM gcc:latest AS cpp
WORKDIR /code
COPY solution.cpp .
RUN g++ -o solution solution.cpp -std=c++17 -O2 -Wall
CMD ["./solution"]

# ==================== Java ====================
FROM eclipse-temurin:17-jdk AS java
WORKDIR /code
COPY Solution.java .
RUN javac Solution.java
CMD ["java", "Solution"]

# ==================== Python ====================
FROM python:3.11-slim AS python
WORKDIR /code
COPY solution.py .
CMD ["python3", "solution.py"]

# ==================== Go ====================
FROM golang:1.21-alpine AS go
WORKDIR /code
COPY solution.go .
RUN go build -o solution solution.go
CMD ["./solution"]

# ==================== Node.js ====================
FROM node:18-alpine AS node
WORKDIR /code
COPY solution.js .
CMD ["node", "solution.js"]
