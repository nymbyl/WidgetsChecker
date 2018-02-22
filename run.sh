#!/bin/sh

# examples:
# ./run.sh duke.WidgetsKt 2018-02-17
# can specify thread count too
# ./run.sh duke.WidgetsKt 2018-02-17 10

./gradlew createdeps

source .classpath
java -cp $CLASSPATH -Dconfiguration.directory=conf "$@"

