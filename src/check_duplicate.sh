#!/bin/sh
# 使い方: sh check_duplicate_anydigit.sh file1.log file2.log ... fileN.log
# 出力: 複数ファイルに共通して現れた「括弧内の数字」

for file in "$@"; do
  grep 'MyTeamHumanDetector' "$file" \
    | grep -oE '\([0-9]+\)' \
    | tr -d '()'
done \
  | sort -n \
  | uniq -d
