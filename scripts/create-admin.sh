#!/usr/bin/env bash
# Tạo tài khoản quản trị viên duy nhất trên máy chủ (NCL-03-CN-002). Chạy một lần khi cài đặt:
#
#   scripts/create-admin.sh admin@example.com "Quản trị viên"
#
# Mật khẩu nhập ẩn từ bàn phím (hoặc đặt sẵn biến LC_ADMIN_PASSWORD), không truyền qua tham số dòng lệnh.
# Đã có quản trị viên thì câu lệnh từ chối và thoát mã 2. Biến LC_APP_JAR trỏ tới tệp jar đã dựng;
# để trống thì chạy từ mã nguồn bằng Maven wrapper.
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Cách dùng: $0 <địa chỉ thư> [tên hiển thị]" >&2
  exit 1
fi

email="$1"
name="${2:-Quản trị viên}"

if [[ -z "${LC_ADMIN_PASSWORD:-}" ]]; then
  read -r -s -p "Mật khẩu quản trị viên (ít nhất 10 ký tự): " LC_ADMIN_PASSWORD
  echo
  read -r -s -p "Nhập lại mật khẩu: " confirm
  echo
  if [[ "$LC_ADMIN_PASSWORD" != "$confirm" ]]; then
    echo "Hai lần nhập mật khẩu không khớp." >&2
    exit 1
  fi
fi
# Java đọc biến môi trường theo locale của hệ thống: máy chủ chưa đặt locale thì dùng UTF-8 để giữ dấu tiếng Việt.
if [[ -z "${LC_ALL:-}${LANG:-}" ]]; then
  export LANG=C.UTF-8
fi

# Tên hiển thị có thể chứa khoảng trắng nên truyền qua biến môi trường.
export LC_ADMIN_PASSWORD
export LC_ADMIN_NAME="$name"

args=(--server.port=0 --create-admin "--email=$email")
if [[ -n "${LC_APP_JAR:-}" ]]; then
  exec java -jar "$LC_APP_JAR" "${args[@]}"
fi
cd "$(dirname "$0")/../backend"
exec ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="${args[*]}"
