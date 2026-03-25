#!/bin/bash

echo "🔍 Stopping the Gradle daemon..."
gradle --stop 2>/dev/null

echo "🔪 Force kill all Gradle/Java related processes..."
pkill -f 'gradle.*daemon' 2>/dev/null
pkill -f 'java.*gradle' 2>/dev/null
pkill -f 'gradle' 2>/dev/null

echo "✅ Gradle is all cleaned up! Memory is freed."


