#!/bin/sh

#docker stop $(docker ps -q --filter ancestor=local-ticket-ibm-backend:latest)
#docker stop (docker ps -q --filter ancestor=local-ticket-ibm-backend:latest)
docker container kill local-ticket-ibm-backend
docker rm local-ticket-ibm-backend -f
