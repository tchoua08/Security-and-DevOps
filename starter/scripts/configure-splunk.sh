#!/bin/sh
set -eu

SPLUNK="/opt/splunk/bin/splunk"
AUTH="admin:Password123!"
INDEX="security_devops"
SOURCETYPE="sareeta:app"
LOG_FILE="/tmp/security-devops-events.log"

"$SPLUNK" list index "$INDEX" -auth "$AUTH" >/dev/null 2>&1 || \
  "$SPLUNK" add index "$INDEX" -auth "$AUTH"

cat > "$LOG_FILE" <<'LOGS'
2026-06-29T15:30:00Z INFO CreateUser success username=splunk-user status=201 path=/api/user/create
2026-06-29T15:30:05Z WARN CreateUser failed username=bad-user status=400 reason=password_validation
2026-06-29T15:30:12Z INFO Login success username=splunk-user status=200 path=/login
2026-06-29T15:30:20Z INFO SubmitOrder success username=splunk-user status=200 path=/api/order/submit/splunk-user
2026-06-29T15:30:24Z WARN SubmitOrder failed username=missing-user status=403 reason=forbidden_owner_mismatch
2026-06-29T15:30:30Z ERROR Exception controller=OrderController type=AccessDeniedException message=Authenticated user cannot submit another user's order
LOGS

"$SPLUNK" add oneshot "$LOG_FILE" \
  -index "$INDEX" \
  -sourcetype "$SOURCETYPE" \
  -host local-dev \
  -auth "$AUTH" >/dev/null

curl -sk -u "$AUTH" \
  https://localhost:8089/servicesNS/admin/search/saved/searches/Security%20DevOps%20Failure%20Alert \
  -X DELETE >/dev/null 2>&1 || true

curl -sk -u "$AUTH" \
  https://localhost:8089/servicesNS/admin/search/saved/searches \
  -d name='Security DevOps Failure Alert' \
  --data-urlencode search='search index=security_devops sourcetype=sareeta:app ("CreateUser failed" OR "SubmitOrder failed" OR Exception)' \
  -d is_scheduled=1 \
  -d cron_schedule='*/5 * * * *' \
  -d dispatch.earliest_time='-15m' \
  -d dispatch.latest_time='now' \
  -d alert_type='number of events' \
  -d alert_comparator='greater than' \
  -d alert_threshold=0 \
  -d actions='addto_triggered_alerts' \
  -d alert.track=1 >/dev/null

"$SPLUNK" search 'search index=security_devops sourcetype=sareeta:app | stats count by host sourcetype' \
  -auth "$AUTH" \
  -preview false \
  -output table

curl -sk -u "$AUTH" \
  https://localhost:8089/servicesNS/admin/search/saved/searches/Security%20DevOps%20Failure%20Alert \
  | grep -E '<title>|alert_type|cron_schedule|alert_threshold|dispatch.earliest_time'
