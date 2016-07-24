#!/bin/bash

if [ -f bookmarx.pid ]; then
    kill -9 $(cat bookmarx.pid)
fi

nohup lein trampoline run server > /var/log/bookmarx.log & echo $! > /var/run/bookmarx.pid
