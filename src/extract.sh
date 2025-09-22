#!/bin/sh
# 使い方: sh extract.sh file1.log file2.log file3.log
# 出力形式: "<数字>, <何番目のファイルか>"

i=0
for file in "$@"; do
  i=$((i+1))
  grep 'MyTeamHumanDetector' "$file" \
    | grep -oE '\([0-9]+\)' \
    | tr -d '()' \
    | grep -E '^[0-9]{3}$' \
    | sort -n \
    | awk -v idx="$i" '{print $0 ", " idx}'
done
