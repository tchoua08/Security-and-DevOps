#!/bin/sh
set -eu

BASE_URL="${BASE_URL:-http://localhost:8080}"
OUT_DIR="${OUT_DIR:-evidence/api}"
USERNAME="${USERNAME:-splunk-user-$(date +%s)}"
PASSWORD="${PASSWORD:-password1}"

mkdir -p "$OUT_DIR"

curl -s -i -X POST "$BASE_URL/api/user/create" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\",\"confirmPassword\":\"$PASSWORD\"}" \
  > "$OUT_DIR/01-create-user-success.txt"

curl -s -i -X POST "$BASE_URL/api/user/create" \
  -H "Content-Type: application/json" \
  -d '{"username":"bad-user","password":"short","confirmPassword":"short"}' \
  > "$OUT_DIR/02-create-user-failure.txt"

TOKEN="$(curl -s -i -X POST "$BASE_URL/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" \
  | awk -F': ' '/^Authorization:/ {gsub("\r", "", $2); print $2}')"

printf '%s\n' "$TOKEN" > "$OUT_DIR/03-jwt-token.txt"

curl -s -i "$BASE_URL/api/item" \
  -H "Authorization: $TOKEN" \
  > "$OUT_DIR/04-items-success.txt"

curl -s -i -X POST "$BASE_URL/api/order/submit/$USERNAME" \
  -H "Authorization: $TOKEN" \
  > "$OUT_DIR/05-order-success.txt"

curl -s -i -X POST "$BASE_URL/api/order/submit/missing-user" \
  -H "Authorization: $TOKEN" \
  > "$OUT_DIR/06-order-forbidden.txt"
