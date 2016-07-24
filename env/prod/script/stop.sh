#!/bin/bash

kill -9 $(cat /var/run/bookmarx.pid)
rm /var/run/bookmarx.pid
