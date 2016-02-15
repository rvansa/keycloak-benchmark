#!/bin/bash

SERVER=$1
DC_ADDRESS=$2
LOG_DIR=$3


export JBOSS_HOME=/tmp/$SERVER
export HOSTNAME=$SERVER

if [ "x$LOG_DIR" != "x" ]; then
    SERVER_OPTS=-Djboss.server.log.dir=$LOG_DIR/$SERVER
fi

$JBOSS_HOME/bin/domain.sh --master-address=$DC_ADDRESS -b $SERVER -bmanagement $SERVER -Djboss.bind.address.private=$SERVER $SERVER_OPTS &
ROOT_PID=$!
echo $ROOT_PID > /tmp/$SERVER/pid;
