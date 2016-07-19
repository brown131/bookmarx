#!/bin/bash

nohup lein trampoline run server > bookmarx.log & echo $! > bookmarx.pid
