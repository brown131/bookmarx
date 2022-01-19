#!/bin/sh
docker run -d -p 3449:3449 -v $(pwd)/dev-resources/redis:/var/lib/redis --rm --name bookmarx brown131/bookmarx:latest $@
