#!/bin/bash

SERVER=$1
DC_ADDRESS=$2

export JBOSS_HOME=/tmp/$SERVER
export HOSTNAME=$SERVER

$JBOSS_HOME/bin/domain.sh --master-address=$DC_ADDRESS -b $SERVER -bmanagement $SERVER -Djboss.bind.address.private=$SERVER &
ROOT_PID=$!
echo $ROOT_PID > /tmp/$SERVER/pid;
