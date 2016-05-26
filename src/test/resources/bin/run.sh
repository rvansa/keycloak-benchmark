#!/bin/bash

#################################### INFO ####################################
# The benchmark is configurable using file passed as first argument to this  #
# script. The properties file *must* contain at least these variables:       #
#                                                                            #
# * SERVERS = array with server addresses                                    #
# * DRIVERS = array with driver addresses                                    #
# * KEYCLOAK_DIST = location of keycloak server distribution                 #
#                                                                            #
# Then, you can configure the database using variables DB_ADDRESS, DB_NAME,  #
# DB_USER and DB_PASSWORD. The PostgreSQL database *must* be running there   #
# (this script does not setup DB).                                           #
#                                                                            #
# Additional parameters configurable through the properties file are:        #
# * LOADER_ARGS = any parameters passed to loader                            #
# * DRIVER_ARGS = any parameters passed to drivers                           #
# * RSH = remote shell command (default is ssh)                              #
# * RCP = remote copy command (default is scp)                               #
# * DC_DIR = directory for the domain controller                             #
# * LOG_DIR = directory for the logs                                         #
# * SERVER_PORT = TCP port of server (defaults to 8080)                      #
# * APP_ADDRESS = Address of the server for backchannel operations           #
# * APP_PORT = Port of the server for backchannel operations                 #
##############################################################################


DIR=$(dirname $0)
PROPERTIES=${1}
DC_ADDRESS=$HOSTNAME

source $DIR/include.sh

if [ "x$PROPERTIES" = "x" ]; then
    echo "No properties file defined."
    exit 1
elif [ -e $PROPERTIES ]; then
    source $PROPERTIES
else
    "File $PROPERTIES does not exist, terminating."
    exit 1
fi
RSH=${RSH:-ssh}
RCP=${RCP:-scp}
DB_ADDRESS=${DB_ADDRESS:-$HOSTNAME}
DB_NAME=${DB_NAME:-test}
DB_USER=${DB_USER:-test}
DB_PASSWORD=${DB_PASSWORD:-test}
DB_DRIVER=${DB_DRIVER:-postgresql}
DB_PROTOCOL=${DB_PROTOCOL:-$DB_DRIVER}
DC_DIR=${DC_DIR:-/tmp/master}
SERVER_NAME=${SERVER_NAME:-Keycloak}
SERVER_PORT=${SERVER_PORT:-8080}
APP_ADDRESS=${APP_ADDRESS:-$HOSTNAME}
APP_PORT=${APP_PORT:-8080}

if [ ${#SERVERS} -le 0 ]; then
    echo "No servers defined."
    exit 1
elif [ ${#DRIVERS} -le 0 ]; then
    echo "No drivers defined."
    exit 1
elif [ "x$KEYCLOAK_DIST" = "x" ]; then
    echo "Keycloak distribution not defined."
    exit 1
fi

SERVER_LIST=$(printf "%s:${SERVER_PORT}," "${SERVERS[@]}")
DRIVER_LIST=$(printf "%s," "${DRIVERS[@]}")

if [ "x$NO_PREPARE" = "x" ]; then
    # Prepare domain controller
    echo "Preparing domain controller..."
    rm -rf $DC_DIR
    mkdir $DC_DIR
    export JBOSS_HOME=$DC_DIR
    if [[ $KEYCLOAK_DIST == *.tar.gz ]]; then
	    tar -xzf $KEYCLOAK_DIST -C $DC_DIR --strip-components=1
    elif [[ $KEYCLOAK_DIST == *.zip ]]; then
    	    unzip -n $KEYCLOAK_DIST -d $DC_DIR && mv $DC_DIR/`ls $DC_DIR`/* $DC_DIR
    else
	echo "ERROR: Cannot unzip domain controller";
	exit 1;
    fi
    echo $DIR
    ls -l $DIR/../server
    cat $DIR/../server/domain.xml | \
        sed 's/db-address-to-be-replaced/'$DB_ADDRESS'/' | \
        sed 's/db-name-to-be-replaced/'$DB_NAME'/' | \
        sed 's/db-user-to-be-replaced/'$DB_USER'/' | \
        sed 's/db-password-to-be-replaced/'$DB_PASSWORD'/' | \
	sed 's/db-protocol-to-be-replaced/'$DB_PROTOCOL'/' | \
	sed 's/db-driver-to-be-replaced/'$DB_DRIVER'/' \
        >  $DC_DIR/domain/configuration/domain.xml
    cp $DIR/../server/host-master.xml $DC_DIR/domain/configuration
    echo "Domain controller ready."

    # Prepare servers = host controllers
    for SERVER in ${SERVERS[@]}; do
        echo "Copying server distribution to $SERVER..."
        $RCP $KEYCLOAK_DIST $SERVER:/tmp/$(basename $KEYCLOAK_DIST)
        $RCP $DIR/../server/host.xml $DIR/*.sh $SERVER:/tmp
        echo "Preparing server $SERVER..."
        $RSH $SERVER "chmod a+x /tmp/prepare.sh && /tmp/prepare.sh $SERVER $(basename $KEYCLOAK_DIST)"
        $DIR/add-user.sh -u $SERVER -p admin -dc $DC_DIR/domain/configuration
        echo "Server $SERVER ready."
    done

    for DRIVER in ${DRIVERS[@]} $APP_ADDRESS; do
        echo "Copying benchmark to $DRIVER"
        $RCP $DIR/../keycloak-benchmark.jar $DIR/../keycloak-benchmark-tests.jar $DRIVER:/tmp
        echo "Driver/app server $DRIVER ready."
    done
fi

if [ "x$NO_SERVER_START" == "x" ]; then
    echo "Starting domain controller..."
    $DC_DIR/bin/domain.sh --host-config=host-master.xml -bmanagement $DC_ADDRESS &> /dev/null &
    DC_PID=$!

    # Some shells (mrsh) don't return until whole process tree finishes, therefore, we have to
    # let it run on background and parse output locally
    SERVER_FD=3
    for SERVER in ${SERVERS[@]}; do
        eval "exec $SERVER_FD< <($RSH $SERVER /tmp/start_server.sh $SERVER $DC_ADDRESS $LOG_DIR)"
        ((SERVER_FD++))
    done;
    LAST_FD=$((SERVER_FD - 1))
    for SERVER_FD in `seq 3 $LAST_FD`; do
        while eval "read <&$SERVER_FD line"; do
            if [[  $line =~ $SERVER_NAME.*started.*\ in && ! ($line =~ Host\ Controller) ]]; then
                echo $line
                # pipe the output to /dev/null so that server is not blocked when it fills up the buffer
                eval "cat <&$SERVER_FD > /dev/null " &
                break;
            fi
        done
    done

fi

CP="$DIR/../keycloak-benchmark.jar:$DIR/../keycloak-benchmark-tests.jar"
if [ "x$NO_LOADER" = "x" ]; then
    echo $(date +"%H:%M:%S") "Loading data to server..."
    if java -cp $CP $LOADER_ARGS -Dtest.servers=$SERVER_LIST -Dtest.app=$APP_ADDRESS:$APP_PORT org.jboss.perf.Loader ; then
        echo $(date +"%H:%M:%S") "Data loaded"
    else
        echo "Failed to load data!"
        exit 1
    fi
fi

CP="/tmp/keycloak-benchmark.jar:/tmp/keycloak-benchmark-tests.jar"
echo "Starting dummy app server..."
$RSH $APP_ADDRESS "JAVA_HOME=$JAVA_HOME $JAVA_HOME/bin/java -cp $CP $DRIVER_ARGS -Dtest.app=$APP_ADDRESS:$APP_PORT -Djava.net.preferIPv4Stack=true org.jboss.perf.AppServer" &

echo "Starting test..."
START_DRIVER_PIDS=""
for INDEX in ${!DRIVERS[@]}; do
    DRIVER=${DRIVERS[$INDEX]}
    $RSH $DRIVER rm -rf /tmp/$DRIVER
    $RSH $DRIVER "JAVA_HOME=$JAVA_HOME $JAVA_HOME/bin/java -cp /tmp/keycloak-benchmark.jar:/tmp/keycloak-benchmark-tests.jar $DRIVER_ARGS -Dtest.servers=$SERVER_LIST -Dtest.app=$APP_ADDRESS:$APP_PORT -Dtest.driver=$INDEX -Dtest.drivers=$DRIVER_LIST -Dtest.dir=/tmp/$DRIVER Engine" &
    START_DRIVER_PIDS="$START_DRIVER_PIDS $!"
done
if [ "x$START_DRIVER_PIDS" != "x" ]; then
    wait $START_DRIVER_PIDS
fi

echo "Collecting simulation data..."
rm -rf /tmp/report
mkdir /tmp/report
COLLECT_PIDS=""
for DRIVER in ${DRIVERS[@]}; do
    $RCP $DRIVER:'/tmp/'$DRIVER'/results/*/simulation.log' /tmp/report/${DRIVER}-simulation.log &
    COLLECT_PIDS="$COLLECT_PIDS $!"
done
if [ "x$COLLECT_PIDS" != "x" ]; then
    wait $COLLECT_PIDS
fi
java -cp $CP -Dtest.report=/tmp/report Report

if [ "x$LOG_DIR" != "x" ]; then
    JFR_DIR=$LOG_DIR
else
    JFR_DIR=/tmp
fi

echo "Dumping JFRs to $JFR_DIR... "
for SERVER in ${SERVERS[@]}; do
    $RSH $SERVER 'jcmd `jps -vm | grep FlightRecorder | cut -f 1 -d " "` JFR.dump name='${SERVER}' filename='${JFR_DIR}'/'${SERVER}'.jfr'
done

echo "Killing servers..."
for SERVER in ${SERVERS[@]}; do
    $RSH $SERVER /tmp/stop_server.sh $SERVER
done;
killtree $DC_PID 9
echo "Servers killed."
