#!/bin/bash
SERVER=$1

DIR=$(dirname $0)
source $(dirname $0)/include.sh

ROOT_PID=`cat /tmp/$SERVER/pid`
killtree $ROOT_PID 9