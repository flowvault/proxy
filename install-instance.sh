#!/bin/bash

# Usage:
#   install-instance.sh <image>
#
# Example:
#   install-instance.sh flowvault/proxy:0.6.46

args=$#

if [ "$#" -ne 1 ]; then
  echo "Usage: `basename $0` <version>"
  exit $E_NOARGS
fi

set -x #echo on

docker stop `docker ps -q`;
docker rmi -f `docker images -q`;
docker rm $(docker ps -qa --no-trunc --filter "status=exited")
docker run --log-opt max-size=1g --log-opt max-file=10 -d -p 7000:9000 $1 production;
