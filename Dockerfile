FROM ubuntu:20.04

COPY build/native/nativeCompile /app

ENTRYPOINT /app/allure-testops-utils
