#!/bin/sh
# Run from the project root after compile.sh. Forwards CLI args to headless mode.
# Example: ./execute_headless.sh --level 01 --bridge res/bridges/01.json --max-steps 100000
exec java -cp .:lib/jbox2d-library-2.2.1.1.jar:lib/flatlaf-2.1.jar bridge.Main --headless "$@"
