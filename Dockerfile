FROM openjdk:17-buster

ARG JAR_FILE=target/*.jar

COPY target /

COPY ${JAR_FILE} jira-1.0.jar

ENTRYPOINT ["java","-jar","/jira-1.0.jar"]