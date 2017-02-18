#!/usr/bin/env bash

if [ -f bookmarx.pid ]; then
    kill -9 $(cat bookmarx.pid)
fi

DEBUG=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
CLASSPATH=target/bookmarx.jar:target/bcpkix-jdk15on-1.55.jar:target/bcprov-jdk15on-1.55.jar

nohup java $DEBUG -cp $CLASSPATH clojure.main -m bookmarx.server & echo $! > bookmarx.pid
