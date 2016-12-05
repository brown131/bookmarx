#!/bin/bash 

# Run from bookmarx/ as: env/dev/script/transfer.sh

GPG_AGENT_PATH=/tmp/$(ls /tmp | grep gpg- )/S.gpg-agent
GPG_AGENT_PID=$(ps -A | grep gpg- | sed -e 's/^ *//' | cut -f1 -d" " | head -1)

export GPG_AGENT_INFO=$GPG_AGENT_PATH:$GPG_AGENT_PID:1
export GPG_TTY=$(tty)

# Reset the agent symlink
rm ~/.gnupg/S.gpg-agent
ln -s $GPG_AGENT_PATH ~/.gnupg/S.gpg-agent

lein exec env/dev/clj/bookmarx/transfer.clj
