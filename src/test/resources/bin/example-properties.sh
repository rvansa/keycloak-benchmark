#!/usr/bin/env bash

# Here test1 and test2 must resolve to IP addresses, be it virtual interfaces
# on local machine or physical machines.
SERVERS=(test1)
DRIVERS=(test2)

KEYCLOAK_DIST=$HOME/workspace/keycloak/distribution/server-dist/target/keycloak-1.9.0.Final-SNAPSHOT.tar.gz
HOSTNAME=localhost

SETUP_ARGS="-Dtest.totalUsers=500 -Dtest.activeUsers=200"
LOADER_ARGS="$SETUP_ARGS"
DRIVER_ARGS="$SETUP_ARGS -Dtest.usersPerSecond=10 -Dtest.adminsPerSecond=1 -Dtest.duration=60"