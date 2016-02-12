#!/bin/bash

################# INFO ###########################################################
# To use this benchmark, setup following:
# - running PostgreSQL, set addr/db/user/password using $DB_* variables below.
# - DNS record resolving 'keycloak' to $SERVERS
# -
##################################################################################


RSH=ssh
RCP=scp

SERVERS=( test1 test2 )
DRIVERS=( test3 test4 )
DB_ADDRESS=$HOSTNAME
DB_NAME=test
DB_USER=test
DB_PASSWORD=test
DC_ADDRESS=$HOSTNAME

KEYCLOAK_DIST=$HOME/workspace/keycloak/distribution/server-dist/target/keycloak-1.9.0.CR1-SNAPSHOT.tar.gz
TEST_ARGS=""

DC_DIR=/tmp/master
DIR=$(dirname $0)
source $DIR/include.sh

if [ "x$NO_PREPARE" = "x" ]; then
    # Prepare domain controller
    echo "Preparing domain controller..."
    mkdir $DC_DIR
    tar -xzf $KEYCLOAK_DIST -C $DC_DIR --strip-components=1
    cat $DIR/../server/domain.xml | \
        sed 's/db-address-to-be-replaced/'$DB_ADDRESS'/' | \
        sed 's/db-name-to-be-replaced/'$DB_NAME'/' | \
        sed 's/db-user-to-be-replaced/'$DB_USER'/' | \
        sed 's/db-password-to-be-replaced/'$DB_PASSWORD'/' \
        >  $DC_DIR/domain/configuration/domain.xml
    echo "Domain controller ready."

    # Prepare servers = host controllers
    for SERVER in ${SERVERS[@]}; do
        echo "Copying server distribution to $SERVER..."
        $RCP $KEYCLOAK_DIST $SERVER:/tmp/keycloak-server.tar.gz
        $RCP $DIR/../keycloak-benchmark.jar $DIR/../keycloak-benchmark-tests.jar $SERVER:/tmp
        $RCP $DIR/../server/host.xml $DIR/*.sh $SERVER:/tmp
        echo "Preparing server $SERVER..."
        $RSH $SERVER "chmod a+x /tmp/prepare.sh && /tmp/prepare.sh $SERVER"
        $DIR/add-user.sh -u $SERVER -p admin -dc $DC_DIR/domain/configuration
        echo "Server $SERVER ready."
    done
fi

echo "Starting domain controller..."
export JBOSS_HOME=$DC_DIR
$DC_DIR/bin/domain.sh --host-config=host-master.xml -bmanagement $DC_ADDRESS &> /dev/null &
DC_PID=$!

START_SERVER_PIDS=""
for SERVER in ${SERVERS[@]}; do
    $RSH $SERVER /tmp/start_server.sh $SERVER $DC_ADDRESS &
    START_SERVER_PIDS="$START_SERVER_PIDS $!"
done;
if [ "x$START_SERVER_PIDS" != "x" ]; then
    wait $START_SERVER_PIDS
fi

CP="$DIR/../keycloak-benchmark.jar:$DIR/../keycloak-benchmark-tests.jar"
if [ "x$NO_LOADER" = "x" ]; then
    echo "Loading data to server..."
    java -cp $CP org.jboss.perf.Loader
    echo "Data loaded"
fi

echo "Starting test..."
START_DRIVER_PIDS=""
for INDEX in ${!DRIVERS[@]}; do
    DRIVER=${DRIVERS[$INDEX]}
    $RSH $DRIVER 'java -cp /tmp/keycloak-benchmark.jar:/tmp/keycloak-benchmark-tests.jar '$TEST_ARGS' -Dtest.driver='$INDEX' -Dtest.drivers='${#DRIVERS[@]}' -Dtest.dir=/tmp/'$DRIVER' Engine' &
    START_DRIVER_PIDS="$START_DRIVER_PIDS $!"
done
if [ "x$START_DRIVER_PIDS" != "x" ]; then
    wait $START_DRIVER_PIDS
fi

echo "Collecting simulation data..."
mkdir /tmp/report
COLLECT_PIDS=""
for DRIVER in ${DRIVERS[@]}; do
    $RCP $DRIVER '/tmp/'$DRIVER'/results/*/simulation.log' /tmp/report/${DRIVER}-simulation.log &
    COLLECT_PIDS="$COLLECT_PIDS $!"
done
if [ "x$COLLECT_PIDS" != "x" ]; then
    wait $COLLECT_PIDS
fi
head -n 1 /tmp/report/${DRIVERS[0]}-simulation.log > /tmp/report/simulation.log
for DRIVER in ${DRIVERS[@]}; do
    tail -n +2 /tmp/report/${DRIVER}-simulation.log >> /tmp/report/simulation.log
done
java -cp $CP -Dtest.report=/tmp/report Report

echo "Killing servers..."
for SERVER in ${SERVERS[@]}; do
    $RSH $SERVER /tmp/stop_server.sh $SERVER
done;
killtree $DC_PID 9
echo "Servers killed."
