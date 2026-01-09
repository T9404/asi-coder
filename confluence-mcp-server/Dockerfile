FROM gradle:8.4.0-jdk21 AS build
ARG CONFLUENCE_URL
ARG CONFLUENCE_USERNAME
ARG CONFLUENCE_API_TOKEN
ARG SERVER_PORT

ENV CONFLUENCE_URL=${CONFLUENCE_URL}
ENV CONFLUENCE_USERNAME=${CONFLUENCE_USERNAME}
ENV CONFLUENCE_API_TOKEN=${CONFLUENCE_API_TOKEN}
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
