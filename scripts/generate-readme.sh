#!/bin/sh

cd $(dirname $0)/..

function header {
echo '<div>'
  echo '<div align="left">'
    echo '<img src="https://github.com/CryZo/PaintballNotificator/actions/workflows/android.yml/badge.svg"/>'
    echo '<img src="https://github.com/CryZo/PaintballNotificator/actions/workflows/docs.yml/badge.svg"/>'
  echo '</div>'
  echo '<div align="right">'
    echo -n '<a href="README.md">English</a> | '
    echo '<a href="README.de.md">Deutsch</a>'
  echo '</div>'
echo '</div>'
}

function generate_readme {
  OUT=$1
  LANG=$2
  FASTLANE_PREFIX=fastlane/metadata/android/$LANG

  header > $OUT
  echo '' >> $OUT

  echo '<div align="center">' >> $OUT
  echo "<img src=\"$FASTLANE_PREFIX/images/icon.png\" alt=\"App icon\" />" >> $OUT
  echo '</div>' >> $OUT
  echo '' >> $OUT

  echo "<h1>$(cat $FASTLANE_PREFIX/title.txt)<br><sub>$(cat $FASTLANE_PREFIX/short_description.txt)</sub></h1>" >> $OUT
  echo '' >> $OUT
  echo "> $(cat $FASTLANE_PREFIX/full_description.txt)" >> $OUT
  echo '' >> $OUT


  echo "## Screenshots" >> $OUT

  echo -n "| " >> $OUT
  for file in $FASTLANE_PREFIX/images/phoneScreenshots/*
  do
    echo -n " |" >> $OUT
  done
  echo "" >> $OUT

  echo -n "|" >> $OUT
  for file in $FASTLANE_PREFIX/images/phoneScreenshots/*
  do
    echo -n "-|" >> $OUT
  done
  echo "" >> $OUT

  echo -n "| " >> $OUT
  for file in $FASTLANE_PREFIX/images/phoneScreenshots/*
  do
    echo -n "![]($file) | " >> $OUT
  done
  echo -e '\n' >> $OUT


  echo "## Changelog" >> $OUT
  for file in $FASTLANE_PREFIX/changelogs/*
  do
    cat $file >> $OUT
    echo -e '\n' >> $OUT
  done
}

generate_readme README.md en-US
generate_readme README.de.md de