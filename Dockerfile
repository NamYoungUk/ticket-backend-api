FROM openjdk:8-jdk-alpine

RUN apk add --no-cache tzdata && \
    apk add zip && \
    apk add vim

ENV LOG4J_FORMAT_MSG_NO_LOOKUPS=true
ENV TICKET_APP_VER=1.1
ENV TICKET_APP_PATH=/ticket/app
ENV TICKET_APP_DATA_PATH=/ticket/appdata
ENV TICKET_APP_CONFIG_PATH=${TICKET_APP_DATA_PATH}/conf
ENV TICKET_APP_REPORT_PATH=${TICKET_APP_DATA_PATH}/reports
ENV TICKET_APP_LOG_PATH=${TICKET_APP_DATA_PATH}/logs
ENV JAVA_OPTS=""
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
# Set Timezone
ENV TZ=Asia/Seoul
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /${TICKET_APP_PATH} \
             /${TICKET_APP_DATA_PATH} \
             /${TICKET_APP_CONFIG_PATH} \
             /${TICKET_APP_REPORT_PATH} \
             /${TICKET_APP_LOG_PATH}

ADD ./target/ticket.ibm.api-${TICKET_APP_VER}.jar /${TICKET_APP_PATH}/ticket.ibm.api-${TICKET_APP_VER}.jar

USER root

EXPOSE 8099

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -jar /${TICKET_APP_PATH}/ticket.ibm.api-${TICKET_APP_VER}.jar"]
