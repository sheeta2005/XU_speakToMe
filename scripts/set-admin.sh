#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# 10/20：先校验整数参数，防止把任意文本拼入管理员初始化 SQL。
case "${1:-}" in ''|*[!0-9]*|0) echo '用法：sh scripts/set-admin.sh 正整数用户ID' >&2; exit 1;; esac
printf 'UPDATE `user` SET role=1 WHERE id=%s AND status=1; SELECT id,nickname,role,status FROM `user` WHERE id=%s;\n' "$1" "$1" |
  docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"'
echo '请核对查询结果：role=1 且 status=1 才有管理权限；空结果表示用户不存在。'
