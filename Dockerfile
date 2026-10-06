FROM eclipse-temurin:21-jre-jammy
ENV TZ=Asia/Shanghai
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home /app app && mkdir -p /app/logs/csp && chown app:app /app/logs /app/logs/csp
COPY --chown=app:app app.jar /app/app.jar
USER app
ENTRYPOINT ["java","-jar","/app/app.jar"]
