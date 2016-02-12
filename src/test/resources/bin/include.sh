#!/bin/bash

killtree() {
    local _pid=$1
    local _sig=$2
    kill -STOP ${_pid}
    for _child in $(ps -o pid --no-headers --ppid ${_pid}); do
        killtree ${_child} ${_sig}
    done
    kill -${_sig} ${_pid}
    kill -CONT ${_pid}
}


