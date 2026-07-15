#!/bin/sh

# Lightweight Maven wrapper for environments where Maven is not installed.
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.11
MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_BIN="${MAVEN_HOME}/bin/mvn"

if [ ! -x "$MAVEN_BIN" ]; then
    ARCHIVE="${TMPDIR:-/tmp}/apache-maven-${MAVEN_VERSION}.zip"
    URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"
    mkdir -p "$(dirname -- "$MAVEN_HOME")"
    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --silent --show-error "$URL" --output "$ARCHIVE"
    elif command -v wget >/dev/null 2>&1; then
        wget --quiet "$URL" --output-document="$ARCHIVE"
    else
        echo "The Maven wrapper requires curl or wget." >&2
        exit 1
    fi
    rm -rf "$MAVEN_HOME"
    mkdir -p "$MAVEN_HOME"
    if command -v unzip >/dev/null 2>&1; then
        unzip -q "$ARCHIVE" -d "$(dirname -- "$MAVEN_HOME")"
    else
        echo "The Maven wrapper requires unzip." >&2
        exit 1
    fi
    rm -f "$ARCHIVE"
fi

exec "$MAVEN_BIN" -f "$BASE_DIR/pom.xml" "$@"

