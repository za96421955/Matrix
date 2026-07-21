#!/bin/bash

cd "$(dirname "$0")" || exit 1
./stop.sh
sleep 2
./start.sh
