#!/bin/sh

# Based on suggestion from https://robolectric.org/blog/2023/11/11/improving-android-all-downloading/
# and https://github.com/utzcoz/robolectric-android-all-fetcher

VERSIONS="10-robolectric-5803371 11-robolectric-6757853 12-robolectric-7732740 12.1-robolectric-8229987 13-robolectric-9030017 14-robolectric-10818077 4.4_r1-robolectric-r2 5.0.2_r3-robolectric-r0 5.1.1_r9-robolectric-r2 6.0.1_r3-robolectric-r1 7.0.0_r1-robolectric-r1 7.1.0_r7-robolectric-r1 8.0.0_r4-robolectric-r1 8.1.0-robolectric-4611349 9-robolectric-4913185-2"

for version in $VERSIONS; do
  echo $(date): Fetching Robolectric ${version}
  mvn dependency:get -Dartifact=org.robolectric:android-all:${version} --no-transfer-progress
  mvn dependency:get -Dartifact=org.robolectric:android-all-instrumented:${version}-i6 --no-transfer-progress
done
