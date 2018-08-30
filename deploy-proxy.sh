#!/bin/bash

# Usage:
#   deploy-proxy.sh <version>
#
# Example:
#   deploy-proxy.sh 0.0.13

args=$#

if [ -z "$1" ]
then
  echo "Usage: `basename $0` <version>"
  exit $E_NOARGS
fi

set -x #echo on

sudo docker stop `docker ps -q`;
sudo docker rmi -f `docker images -q`;
sudo docker rm $(docker ps -qa --no-trunc --filter "status=exited")
sudo docker run -d -p 7000:9000 flowvault/proxy:$1 production;
