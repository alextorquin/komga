#!/bin/bash

mkdir /var/data
mkdir /var/data/komga
mkdir /var/data/komga/config
mkdir /var/data/komga/data
mkdir /var/data/komga/media
docker-compose -f komga-docker.yml up -d
