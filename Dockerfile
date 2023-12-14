FROM ubuntu:20.04

COPY build/native/nativeCompile/allure-testops-utils /app/allure-testops-utils

ENTRYPOINT /app/allure-testops-utils
