#!/bin/sh

# example: ./run.sh duke.WidgetsKt 2018-02-17

./gradlew createdeps

source .classpath
java -cp $CLASSPATH -Dconfiguration.directory=conf "$@"

