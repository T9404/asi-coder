FROM gradle:8.4.0-jdk21 AS build
ARG YOUTRACK_URL
ARG YOUTRACK_TOKEN
ARG SERVER_PORT

ENV YOUTRACK_URL=${YOUTRACK_URL}
ENV YOUTRACK_TOKEN=${YOUTRACK_TOKEN}
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