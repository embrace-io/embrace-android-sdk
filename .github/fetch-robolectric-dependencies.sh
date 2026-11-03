#!/bin/sh

# Based on suggestion from https://robolectric.org/blog/2023/11/11/improving-android-all-downloading/
# and https://github.com/utzcoz/robolectric-android-all-fetcher

VERSIONS="17-robolectric-15733970 6.0.1_r3-robolectric-r1"

for version in $VERSIONS; do
  echo $(date): Fetching Robolectric ${version}
  mvn dependency:get -Dartifact=org.robolectric:android-all-instrumented:${version}-i7 --no-transfer-progress
done
