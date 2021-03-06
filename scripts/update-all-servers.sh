#!/bin/bash

SERVERS=`cat all-servers.txt`
DIR="tigase-server"

for s in ${SERVERS} ; do 

  scp jars/tigase-server.jar tigase@${s}:${DIR}/jars/
  scp libs/tigase-* tigase@${s}:${DIR}/libs/
  scp src/main/groovy/tigase/admin/*.groovy tigase@${s}:${DIR}/scripts/admin/

done
