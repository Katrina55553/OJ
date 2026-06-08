FROM gcc:latest
WORKDIR /code
COPY solution.cpp .
RUN g++ -o solution solution.cpp -std=c++17 -O2 -Wall 2>&1
CMD ["./solution"]
