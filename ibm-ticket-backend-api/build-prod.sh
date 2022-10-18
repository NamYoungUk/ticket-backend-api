#!/bin/sh
# Compile Application
mvn clean package -DskipTests=true -P sk-prod

# Docker image build
docker image build -f Dockerfile -t ticket-ibm-api:202201031200 .

# Save docker image as tar
#mkdir docker-images
#docker save -o ./docker-images/ticket-ibm-api-sk-202201031200.tar ticket-ibm-api:202201031200

## Docker image tag for latest
docker tag ticket-ibm-api:202201031200 ticket-ibm-backend:latest

echo "  ____                     _ "
echo " |  _ \  ___  _ __   ___  | |"
echo " | | | |/ _ \| '_ \ / _ \ | |"
echo " | |_| | (_) | | | |  __/ |_|"
echo " |____/ \___/|_| |_|\___| (_)"