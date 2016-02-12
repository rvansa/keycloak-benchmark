#!/bin/bash

SERVER=$1
DC_ADDRESS=$2

export JBOSS_HOME=/tmp/$SERVER
export HOSTNAME=$SERVER

exec 3< <($JBOSS_HOME/bin/domain.sh --master-address=$DC_ADDRESS -b $SERVER -bmanagement $SERVER -Djboss.bind.address.private=$SERVER)
ROOT_PID=$!
echo $ROOT_PID > /tmp/$SERVER/pid;

echo "Waiting for $SERVER to start..."
while read <&3 line; do
    if [[  $line =~ INFO.*Keycloak.*started\ in && ! ($line =~ Host\ Controller) ]]; then
        echo $line
        break;
        3<&- # close the fd
    fi
done
echo "Server $SERVER started."

