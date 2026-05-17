# Task 19.3 Live Pressure Result

- Date: 2026-05-17
- Script: `pressure/mini-mall-gateway.js`
- Entry point: `api-gateway`
- Load shape: 5 VUs, 15s ramp-up, 60s steady state, 15s ramp-down
- Product: `TASK19-LIVE`
- Gateway rate limit: disabled for this run with `minimall.gateway.rate-limit.enabled=false`
- Order timeout scheduler: disabled for this run with `minimall.order.timeout.enabled=false`
- Docker Desktop + WSL note: Docker k6 could not reach WSL services through `127.0.0.1` host networking, so the run used the current WSL IP as a temporary `BASE_URL`. Do not commit that dynamic IP into project configuration.

## Smoke Run

1 VU smoke run passed before the full run.

```json
{
  "qps": 19.983586010375664,
  "averageResponseMs": 41.41365380597015,
  "p95ResponseMs": 73.05035459999993,
  "transportErrorRate": 0,
  "httpReqFailedRate": 0,
  "apiBusinessFailureRate": 0,
  "apiContractFailureRate": 0,
  "apiExpectedResultFailureRate": 0,
  "negativeInventoryObservedRate": 0,
  "totalRequests": 134,
  "iterations": 11,
  "orderCreateSuccesses": 22,
  "orderCancelSuccesses": 11,
  "paymentSuccesses": 11,
  "reloginAttempts": null
}
```

## Full Run

The full 5 VU run passed all k6 thresholds.

```json
{
  "qps": 40.07067342625951,
  "averageResponseMs": 19.07518615695538,
  "p95ResponseMs": 47.298201099999986,
  "transportErrorRate": 0,
  "httpReqFailedRate": 0,
  "apiBusinessFailureRate": 0,
  "apiContractFailureRate": 0,
  "apiExpectedResultFailureRate": 0,
  "negativeInventoryObservedRate": 0,
  "totalRequests": 3810,
  "iterations": 317,
  "orderCreateSuccesses": 634,
  "orderCancelSuccesses": 317,
  "paymentSuccesses": 317,
  "reloginAttempts": null
}
```

## Database Checks

The final database counts include both the smoke run and the full run.

```text
inventory TASK19-LIVE: available_stock=9672, locked_stock=328, status=ACTIVE
negative_inventory_rows: 0
orders: CANCELLED=328, PAID=328
payments: SUCCESS=328
```

## Notes

`k6 --summary-export /scripts/task19-live-summary.json` could not write through the Docker bind mount because the k6 container user did not have write permission on the mounted directory. The stdout summary above is the actual k6 output captured from the completed run.
