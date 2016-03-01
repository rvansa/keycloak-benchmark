#!/bin/bash

SERVER=$1
DC_ADDRESS=$2
LOG_DIR=$3


export JBOSS_HOME=/tmp/$SERVER
export HOSTNAME=$SERVER

if [ "x$LOG_DIR" != "x" ]; then
    SERVER_OPTS=-Djboss.server.log.dir=$LOG_DIR
fi

$JBOSS_HOME/bin/domain.sh --master-address=$DC_ADDRESS -b $SERVER -bmanagement $SERVER -Djboss.bind.address.private=$SERVER $SERVER_OPTS &
ROOT_PID=$!
echo $ROOT_PID > /tmp/$SERVER/pid;

# Start JFR on the server
# let's wait so the host-controller can start server
sleep 10
SERVER_PID=`jps -vm | grep -e 'FlightRecorder' | cut -f 1 -d " "`
if [ "x$SERVER_PID" != "x" ]; then
    jcmd $SERVER_PID JFR.start filename=$LOG_DIR/$SERVER.jfr settings=profile duration=7200s
else
    echo "Flight Recorder is not available"
fi
