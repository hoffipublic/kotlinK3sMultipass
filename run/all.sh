#!/usr/bin/env bash

SCRIPTDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
REPODIR=${SCRIPTDIR%/*}
cd REPODIR || exit

function runjar() {
    local jarfile
    local currDir="${PWD}"
    local dofzf=true
    for i in {1..6}; do
        if [[ -d "build/libs" ]]; then
            jarfile="$(ls -1 build/libs/*-{all,fat}.jar 2>/dev/null | grep -v metadata)"
            if [[ "$(echo "$jarfile" | wc -l)" = "1" ]]; then
                dofzf=false
                java -jar "$jarfile" "$@"
                break
            fi
        else
            cd ..
        fi
    done
    cd "${currDir}"
    if $dofzf &&  fzf --version >/dev/null && fd --version >/dev/null ; then
        java -jar "$(fd --type f --no-ignore -e jar | fzf --header="jar files:")" "$@"
    fi
}

if [[ -z $1 ]] && fzf --version >/dev/null 2>&1 ; then
    targetEnvArg="$(echo -e "localK3s\nHetzner" | fzf +s --ansi) " # trailing whitespace!
    bootstrapArg="$(echo -e "Bootstrap=false\nBootstrap=true" | fzf +s --ansi) " # trailing whitespace!
fi

runjar "$targetEnvArg$bootstrapArg$@"

