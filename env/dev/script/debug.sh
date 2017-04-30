#!/usr/bin/env bash

PID=bookmarx.pid
LOG=bookmarx.log

if [ -f $PID ]; then
    kill -9 $(cat $PID)
fi

cp ~/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.55/bcprov-jdk15on-1.55.jar ~/.m2/repository/org/bouncycastle/bcpkix-jdk15on/1.55/bcpkix-jdk15on-1.55.jar target

DEBUG=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
CLASSPATH=target/bookmarx.jar:target/bcpkix-jdk15on-1.55.jar:target/bcprov-jdk15on-1.55.jar

nohup java $DEBUG -cp $CLASSPATH clojure.main -m bookmarx.server > $LOG && echo $! > $PID
