#!/bin/bash

set -euo pipefail

setInConfigFile() {
   local fileName="$1"
   local key="$2"
   local value="${3:=''}"

   # escape typical special characters in key / value (. and / for dot-separated keys or path values)
   regexSafeKey=`echo "$key" | sed -r 's/\\//\\\\\//g' | sed -r 's/\\./\\\\\./g'`
   replacementSafeValue=`echo "$value" | sed -r 's/\\//\\\\\//g' | sed -r 's/&/\\\\&/g'`

   if grep --quiet -E "^#?${regexSafeKey}\s*=" ${fileName}; then
      sed -ri "s/^#?(${regexSafeKey}\s*=)[^#\$]*/\1${replacementSafeValue} /" ${fileName}
   else
      echo "${key} = ${value}" >> ${fileName}
   fi
}

if [ ! -f "/var/lib/acosix-transformer/.init-done" ]
then
    if [ ! -f "/var/lib/acosix-transformer/transformer.properties" ]
    then
        touch /var/lib/acosix-transformer/transformer.properties
    fi

    hostName=`hostname`

    # if already set (e.g. mounted config file) we don't want to override
    if ! grep --quiet -E "^application\.host=\s?[^\s]+.*$" /var/lib/acosix-transformer/transformer.properties
    then
        setInConfigFile /var/lib/acosix-transformer/transformer.properties application.host ${hostName}
    fi

    # if already set (e.g. mounted config file) we don't want to override
    if ! grep --quiet -E "^application\.requestLog\.path=\s?[^\s]+.*$" /var/lib/acosix-transformer/transformer.properties
    then
        setInConfigFile /var/lib/acosix-transformer/transformer.properties application.requestLog.path /var/log/acosix-transformer/request.log
    fi

    # otherwise for will also cut on whitespace
    IFS=$'\n'
    for i in `env`
    do
        if [[ $i == T_* ]]
        then
            echo "Processing environment variable $i" > /proc/1/fd/1
            key=`echo "$i" | cut -d '=' -f 1 | cut -d '_' -f 2-`
            value=`echo "$i" | cut -d '=' -f 2-`
            
            # support secrets mounted via files
            if [[ $key == *_FILE ]]
            then
                value="$(< "${value}")"
                key=`echo "$key" | sed -r 's/_FILE$//'`
            fi

            setInConfigFile /var/lib/acosix-transformer/transformer.properties ${key} ${value}
        fi
    done
fi
