#!/bin/bash

PID=bookmarx.pid

if [ -f $PID ]; then
    kill -9 $(cat $PID)
    rm $PID
fi
