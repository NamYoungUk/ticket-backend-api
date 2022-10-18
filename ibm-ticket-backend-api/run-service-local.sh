#!/bin/sh

docker container kill local-ticket-ibm-backend
docker rm local-ticket-ibm-backend -f
#docker run --name local-ticket-ibm-backend -p 8099:8099/tcp local-ticket-ibm-backend:latest
docker run -v c:/dev/sources/SK_CloudZ/ticket/ibm-ticket-backend-api/docker-mount/local/ticket/appdata:/ticket/appdata --name local-ticket-ibm-backend -p 8099:8099/tcp local-ticket-ibm-backend:latest
#docker run -e TICKET_APP_ENV1 --env TICKET_APP_LOG_PATH=/TicketStorage/logs-aaaaaaaaaaa --env TICKET_APP_REPORT_PATH=/TicketStorage/reports-bbbbbbbb -v c:/dev/sources/SK_CloudZ/ticket/ibm-ticket-backend-api/docker-mount/local/TicketStorage:/TicketStorage --name local-ticket-ibm-backend -p 8099:8099/tcp local-ticket-ibm-backend:latest

## Run on docker
#docker run --name local-ticket-ibm-backend -p 8099:8099/tcp -it local-ticket-ibm-backend:latest
#docker run --name local-ticket-ibm-backend -p 8099:8099/tcp local-ticket-ibm-backend:latest
## Run on os
#java -jar ./target/local-ticket.ibm.api-1.0.jar

