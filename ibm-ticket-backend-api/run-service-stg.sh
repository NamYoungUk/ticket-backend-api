#!/bin/sh

docker container kill stg-ticket-ibm-backend
docker rm stg-ticket-ibm-backend -f
#docker run --name stg-ticket-ibm-backend -p 8099:8099/tcp stg-ticket-ibm-backend:latest
docker run -v c:/dev/sources/SK_CloudZ/ticket/ibm-ticket-backend-api/docker-mount/stg/ticket/appdata:/ticket/appdata --name stg-ticket-ibm-backend -p 8099:8099/tcp stg-ticket-ibm-backend:latest
#docker run -e TICKET_APP_ENV1 --env TICKET_APP_LOG_PATH=/TicketStorage/logs-aaaaaaaaaaa --env TICKET_APP_REPORT_PATH=/TicketStorage/reports-bbbbbbbb -v c:/dev/sources/SK_CloudZ/ticket/ibm-ticket-backend-api/docker-mount/local/TicketStorage:/TicketStorage --name local-ticket-ibm-backend -p 8099:8099/tcp local-ticket-ibm-backend:latest

## Run on docker
#docker run --name stg-ticket-ibm-backend -p 8099:8099/tcp -it stg-ticket-ibm-backend:latest
#docker run --name stg-ticket-ibm-backend -p 8099:8099/tcp stg-ticket-ibm-backend:latest
## Run on os
#java -jar ./target/stg-ticket.ibm.api-1.0.jar

