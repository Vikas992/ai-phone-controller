#!/bin/sh
exec java -cp "app/build.gradle" org.gradle.launcher.GradleMain "$@"
