#!/usr/bin/env bash

if [ -f bookmarx.pid ]; then
    kill -9 $(cat bookmarx.pid)
fi

DEBUG=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005

nohup java $DEBUG -cp target/bookmarx.jar clojure.main -m bookmarx.server
