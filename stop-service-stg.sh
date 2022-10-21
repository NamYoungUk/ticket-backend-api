#!/bin/sh

#docker stop $(docker ps -q --filter ancestor=stg-ticket-ibm-backend:latest)
#docker stop (docker ps -q --filter ancestor=stg-ticket-ibm-backend:latest)
docker container kill stg-ticket-ibm-backend
docker rm stg-ticket-ibm-backend -f
