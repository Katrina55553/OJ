FROM eclipse-temurin:17-jdk
WORKDIR /code
COPY Solution.java .
RUN javac Solution.java 2>&1
CMD ["java", "Solution"]
