#!/bin/sh
# Run the simulator with food/restaurants/scenario from one resources folder.
# Usage: ./run-scenario.sh <folder> [maxTicks] [logLevel]
# Example: ./run-scenario.sh scenario1 24 DEBUG

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
RESOURCES="$ROOT/scenarios"
JAR="$ROOT/build/libs/saar-la-carte.jar"

FOLDER=${1:-}
MAX_TICKS=${2:-100}
LOG_LEVEL=${3:-DEBUG}

if [ -z "$FOLDER" ]; then
    echo "Usage: $0 <folder> [maxTicks] [logLevel]" >&2
    echo "Available:" >&2
    ls -1 "$RESOURCES" >&2
    exit 1
fi

DIR="$RESOURCES/$FOLDER"
FOOD="$DIR/food.json"
RESTAURANTS="$DIR/restaurants.json"
SCENARIO="$DIR/scenario.json"

if [ ! -d "$DIR" ]; then
    echo "Unknown scenario folder: $FOLDER" >&2
    echo "Available:" >&2
    ls -1 "$RESOURCES" >&2
    exit 1
fi

for f in "$FOOD" "$RESTAURANTS" "$SCENARIO"; do
    if [ ! -f "$f" ]; then
        echo "Missing file: $f" >&2
        exit 1
    fi
done

echo "Building jar..."
(cd "$ROOT" && ./gradlew jar -q)

exec java -jar "$JAR" \
    --food "$FOOD" \
    --restaurants "$RESTAURANTS" \
    --scenario "$SCENARIO" \
    --maxTicks "$MAX_TICKS" \
    --logLevel "$LOG_LEVEL"
