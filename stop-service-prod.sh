#!/bin/sh

#docker stop $(docker ps -q --filter ancestor=ticket-ibm-backend:latest)
#docker stop (docker ps -q --filter ancestor=ticket-ibm-backend:latest)
docker container kill ticket-ibm-backend
docker rm ticket-ibm-backend -f
