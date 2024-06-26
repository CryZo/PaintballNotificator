#!/bin/bash
cd $(dirname $0)
source ./version.properties

function getVersionCode {
  readarray -d "." -t SPLIT_VERSION <<< $VERSION
  echo $(((${SPLIT_VERSION[0]} * 10000) + (${SPLIT_VERSION[1]} * 100) + ${SPLIT_VERSION[2]}))
}

sed "s|versionCode = .*|versionCode = $(getVersionCode)|" -i build.gradle.kts
sed "s|versionName = .*|versionName = \"$VERSION\"|" -i build.gradle.kts
