#!/bin/bash

set -euo pipefail

JAVA_OPTS=${JAVA_OPTS:-}
# by default we allow 200 threads and swap multipart files to disk after 100 KiB
# => 19.5 MiB heap (expected peak) + ~ 4 MiB app base
# 64 MiB more than sufficient - 32 MiB not default only to account for GC (ideally "new gen only") and static resources on classpath (e.g. probe transformation source files) cached by classloader
JAVA_XMS=${JAVA_XMS:-64M}
JAVA_XMX=${JAVA_XMX:-$JAVA_XMS}

# TBD switch default to true when policy file is drafted
JAVA_SECURITY_ENABLED=${JAVA_SECURITY_ENABLED:-false}
JAVA_DNS_TIMEOUT=${JAVA_DNS_TIMEOUT:-60}

DEBUG=${DEBUG:-false}
DEBUG_PORT=${DEBUG_PORT:-8000}

JAVA_OPTS_DEBUG_CHECK='-agentlib:jdwp=transport=dt_socket,server=[yn],suspend=[yn],address=([^:]+:)?(\d+)'
if [[ $DEBUG == true ]]
then
    if [[ ! $JAVA_OPTS =~ $JAVA_OPTS_DEBUG_CHECK ]]
    then
        JAVA_OPTS="${JAVA_OPTS} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:${DEBUG_PORT}"
    fi
    JAVA_OPTS="${JAVA_OPTS} -Dlogback.configurationFile=/var/lib/acosix-transformer/logback-debug.xml"
fi

if [[ ! $JAVA_OPTS =~ '-Xmx\d+[gGmM]' ]]
then
    JAVA_OPTS="${JAVA_OPTS} -Xmx${JAVA_XMX}"
fi

if [[ ! $JAVA_OPTS =~ '-Xms\d+[gGmM]' ]]
then
    JAVA_OPTS="${JAVA_OPTS} -Xms${JAVA_XMS}"
fi

if [[ ! $JAVA_OPTS =~ '-XX:\+Use(G1|ConcMarkSweep|Serial|Parallel|ParallelOld|ParNew)GC' ]]
then
    JAVA_OPTS="${JAVA_OPTS} -XX:+UseG1GC"

    if [[ ! $JAVA_OPTS =~ '-XX:\+ParallelRefProcEnabled' ]]
    then
        JAVA_OPTS="${JAVA_OPTS} -XX:+ParallelRefProcEnabled"
    fi

    if [[ ! $JAVA_OPTS =~ '-XX:\+UseStringDeduplication' ]]
    then
        JAVA_OPTS="${JAVA_OPTS} -XX:+UseStringDeduplication"
    fi
fi

if [[ ! $JAVA_OPTS =~ '-Dnetworkaddress\.cache\.ttl=' ]]
then
    # explicitly limit JVM DNS cache to 60s to cope with re-mapped IPs of other Docker containers the JVM may depend upon
    JAVA_OPTS="${JAVA_OPTS} -Dnetworkaddress.cache.ttl=${JAVA_DNS_TIMEOUT}"
fi

if [[ $JAVA_SECURITY_ENABLED == true ]]
then
    JAVA_OPTS="${JAVA_OPTS} -Djava.security.manager -Djava.security.policy=/var/lib/acosix-transformer/security.policy"
fi

cd /var/lib/acosix-transformer
exec /sbin/setuser transformer java ${JAVA_OPTS} -jar /var/lib/acosix-transformer/transformer-app.jar > /proc/1/fd/1 2>&1
