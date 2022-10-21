#!/bin/sh
# Compile Application
mvn clean package -DskipTests=true -P local

# Docker image build
docker image build -f Dockerfile -t local-ticket-ibm-api:202201031200 .

# Save docker image as tar
#mkdir docker-images
#docker save -o ./docker-images/local-ticket-ibm-api-sk-202201031200.tar local-ticket-ibm-api:202201031200

## Docker image tag for latest
docker tag local-ticket-ibm-api:202201031200 local-ticket-ibm-backend:latest

echo "  ____                     _ "
echo " |  _ \  ___  _ __   ___  | |"
echo " | | | |/ _ \| '_ \ / _ \ | |"
echo " | |_| | (_) | | | |  __/ |_|"
echo " |____/ \___/|_| |_|\___| (_)"