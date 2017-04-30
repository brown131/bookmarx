#!/bin/bash

PID=bookmarx.pid
LOG=bookmarx.log

if [ -f $PID ]; then
    kill -9 $(cat $PID)
fi

nohup lein trampoline run server > $LOG &
echo $! > $PID
