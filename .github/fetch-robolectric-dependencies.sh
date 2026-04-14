#!/bin/sh

# Based on suggestion from https://robolectric.org/blog/2023/11/11/improving-android-all-downloading/
# and https://github.com/utzcoz/robolectric-android-all-fetcher

VERSIONS="15-robolectric-12650502"

for version in $VERSIONS; do
  echo $(date): Fetching Robolectric ${version}
  mvn dependency:get -Dartifact=org.robolectric:android-all:${version} --no-transfer-progress
  mvn dependency:get -Dartifact=org.robolectric:android-all-instrumented:${version}-i7 --no-transfer-progress
done
