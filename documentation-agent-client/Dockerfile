FROM gradle:8.4.0-jdk21 AS build
ARG OPENAI_API_KEY
ARG CONFLUENCE_MCP_SERVER_URL
ARG SERVER_PORT

ENV OPENAI_API_KEY=${OPENAI_API_KEY}
ENV CONFLUENCE_MCP_SERVER_URL=${CONFLUENCE_MCP_SERVER_URL}
ENV SERVER_PORT=${SERVER_PORT}

WORKDIR /home/gradle/project
COPY --chown=gradle:gradle ../.. .
WORKDIR /home/gradle/project
RUN gradle build --no-daemon --exclude-task test

FROM amazoncorretto:21-alpine-jdk
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/*.jar /app/application.jar
CMD ["java", "-jar", "application.jar"]
EXPOSE ${SERVER_PORT}