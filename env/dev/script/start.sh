#!/bin/bash

if [ -f bookmarx.pid ]; then
    kill -9 $(cat bookmarx.pid)
fi

nohup lein trampoline run server > bookmarx.log & echo $! > bookmarx.pid
