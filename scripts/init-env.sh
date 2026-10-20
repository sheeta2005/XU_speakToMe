#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
# 10/20：仅创建新配置，不覆盖已存在的 AES 密钥与服务密码。
if [ -e .env ]; then
  echo '.env 已存在，初始化已取消，请保留原有密钥。' >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo '请先安装 openssl。' >&2; exit 1; }
umask 077
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    AES_KEY=*|MYSQL_ROOT_PASSWORD=*|MYSQL_PASSWORD=*|REDIS_PASSWORD=*)
      printf '%s=%s\n' "${line%%=*}" "$(openssl rand -base64 32)" ;;
    *) printf '%s\n' "$line" ;;
  esac
done < .env.example > .env
echo '已生成 .env。请保留并备份，接下来运行 docker compose up -d --build --wait。'
