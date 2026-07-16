#!/bin/bash
echo "$1" | sed -n 's/.*"print" *: *"\([^"]*\)".*/\1/p'