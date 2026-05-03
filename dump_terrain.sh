#!/bin/sh
# Run from project root after ./compile.sh.
# Resolves java by full path (JAVA_HOME or /usr/bin/java) so conda/zsh cannot recurse via a `java` function.
# Example: ./dump_terrain.sh --level 01
# Example: ./dump_terrain.sh --level 09 --output res/terrain/09.json
cd "$(dirname "$0")" || exit 1

JAVA_BIN=
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
	JAVA_BIN="${JAVA_HOME}/bin/java"
elif [ -x /usr/bin/java ]; then
	JAVA_BIN=/usr/bin/java
else
	for j in /usr/lib/jvm/*/bin/java; do
		if [ -x "$j" ]; then
			JAVA_BIN=$j
			break
		fi
	done
fi

if [ -z "$JAVA_BIN" ]; then
	echo "dump_terrain.sh: no java found. Install OpenJDK or set JAVA_HOME." >&2
	exit 1
fi

exec "$JAVA_BIN" -cp .:lib/jbox2d-library-2.2.1.1.jar:lib/flatlaf-2.1.jar bridge.Main --dump-terrain "$@"
