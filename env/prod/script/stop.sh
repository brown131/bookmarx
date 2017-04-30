#!/bin/bash

PID=/var/run/bookmarx/bookmarx.pid

if [ -f $PID ]; then
    kill -9 $(cat $PID)
    rm $PID
fi
