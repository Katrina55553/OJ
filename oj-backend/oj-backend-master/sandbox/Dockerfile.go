FROM golang:1.21-alpine
WORKDIR /code
COPY solution.go .
RUN go build -o solution solution.go 2>&1
CMD ["./solution"]
