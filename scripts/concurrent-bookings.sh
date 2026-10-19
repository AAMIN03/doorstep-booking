#!/usr/bin/env bash
# Two POST /bookings at the same time, same JSON body, different Idempotency-Key.
# Pincode 400058 + service 1 has one battery technician, so one call should be 201 and the other 409.
# A second run of the same startTime books an already taken slot: both return 409.

BASE_URL="${BASE_URL:-http://localhost:8080}"
START_TIME="${START_TIME:-$(TZ=Asia/Kolkata date -v+1d -v15H -v0M -v0S +"%Y-%m-%dT15:00:00+05:30")}"
BODY="{\"customerPhone\":\"9876543210\",\"serviceId\":1,\"pincode\":\"400058\",\"startTime\":\"${START_TIME}\"}"
STAMP="$(date +%s)-$$"
DIR="$(mktemp -d)"

echo "POST ${BASE_URL}/bookings"
echo "body ${BODY}"
echo

for i in 1 2; do
  curl -sS -D "${DIR}/${i}.hdr" -o "${DIR}/${i}.body" \
    -X POST "${BASE_URL}/bookings" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: race-${STAMP}-${i}" \
    --data "${BODY}" &
done
wait

for i in 1 2; do
  status="$(awk 'NR==1 { print $2 }' "${DIR}/${i}.hdr")"
  echo "request ${i}  HTTP ${status}"
  cat "${DIR}/${i}.body"
  echo
  echo
done

rm -rf "${DIR}"
