#!/bin/bash

PID=/var/run/bookmarx/bookmarx.pid
LOG=/var/log/bookmarx.log

if [ -f $PID ]; then
    kill -9 $(cat $PID)
fi

nohup lein trampoline run server > $LOG &
echo $! > $PID
