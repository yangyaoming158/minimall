import http from "k6/http";
import { check, fail, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const SUCCESS_CODE = "0";
const ERROR_CODES = {
  BAD_REQUEST: "40000",
  VALIDATION_ERROR: "40001",
  UNAUTHORIZED: "40100",
  NOT_FOUND: "40400",
  CONFLICT: "40900",
  ORDER_CANCELLED: "40901",
  ORDER_INVALID_STATE: "40902",
  PAYMENT_ALREADY_SUCCESS: "40903",
};

const transportErrorRate = new Rate("transport_error_rate");
const apiResponseContractFailureRate = new Rate("api_response_contract_failure_rate");
const apiExpectedResultFailureRate = new Rate("api_expected_result_failure_rate");
const apiBusinessFailureRate = new Rate("api_business_failure_rate");
const negativeInventoryObserved = new Rate("negative_inventory_observed");
const reloginAttempts = new Counter("relogin_attempts");
const orderCreateSuccesses = new Counter("order_create_successes");
const orderCancelSuccesses = new Counter("order_cancel_successes");
const paymentSuccesses = new Counter("payment_successes");

const CONFIG = {
  baseUrl: normalizeBaseUrl(__ENV.BASE_URL),
  username: __ENV.USERNAME,
  password: __ENV.PASSWORD,
  vus: positiveInt(__ENV.VUS, 5),
  duration: __ENV.DURATION || "1m",
  rampUp: __ENV.RAMP_UP || "15s",
  rampDown: __ENV.RAMP_DOWN || "15s",
  sleepSeconds: positiveNumber(__ENV.SLEEP_SECONDS, 1),
  productId: blankToNull(__ENV.PRODUCT_ID),
  productStatus: blankToNull(__ENV.PRODUCT_STATUS) || "ON_SHELF",
  productPage: nonNegativeInt(__ENV.PRODUCT_PAGE, 0),
  productSize: positiveInt(__ENV.PRODUCT_SIZE, 10),
  quantity: positiveInt(__ENV.QUANTITY, 1),
  paymentChannel: blankToNull(__ENV.PAYMENT_CHANNEL) || "MOCK",
  paymentRetryAttempts: positiveInt(__ENV.PAYMENT_RETRY_ATTEMPTS, 5),
  paymentRetrySleepSeconds: positiveNumber(__ENV.PAYMENT_RETRY_SLEEP_SECONDS, 0.2),
  enableOrderFlow: booleanEnv(__ENV.ENABLE_ORDER_FLOW, true),
  enableCancelFlow: booleanEnv(__ENV.ENABLE_CANCEL_FLOW, true),
  enablePaymentFlow: booleanEnv(__ENV.ENABLE_PAYMENT_FLOW, true),
  enableInventoryCheck: booleanEnv(__ENV.ENABLE_INVENTORY_CHECK, true),
  thresholds: {
    transportErrorRate: thresholdValue(__ENV.TRANSPORT_ERROR_RATE_THRESHOLD, "0.01"),
    contractFailureRate: thresholdValue(__ENV.API_CONTRACT_FAILURE_RATE_THRESHOLD, "0.01"),
    expectedResultFailureRate: thresholdValue(__ENV.API_EXPECTED_FAILURE_RATE_THRESHOLD, "0.05"),
    p95Ms: positiveInt(__ENV.P95_THRESHOLD_MS, 1000),
  },
  paths: {
    login: __ENV.LOGIN_PATH || "/api/user/users/login",
    me: __ENV.ME_PATH || "/api/user/users/me",
    productList: __ENV.PRODUCT_LIST_PATH || "/api/product/products",
    productDetail: __ENV.PRODUCT_DETAIL_PATH || "/api/product/products/{productId}",
    inventoryDetail: __ENV.INVENTORY_DETAIL_PATH || "/api/inventory/inventories/{productId}",
    orderCreate: __ENV.ORDER_CREATE_PATH || "/api/order/orders",
    orderDetail: __ENV.ORDER_DETAIL_PATH || "/api/order/orders/{orderNo}",
    orderCancel: __ENV.ORDER_CANCEL_PATH || "/api/order/orders/{orderNo}/cancel",
    paymentPay: __ENV.PAYMENT_PAY_PATH || "/api/payment/payments/{orderNo}/pay",
    paymentDetail: __ENV.PAYMENT_DETAIL_PATH || "/api/payment/payments/{orderNo}",
  },
};

export const options = {
  scenarios: {
    gateway_pressure: {
      executor: "ramping-vus",
      stages: [
        { duration: CONFIG.rampUp, target: CONFIG.vus },
        { duration: CONFIG.duration, target: CONFIG.vus },
        { duration: CONFIG.rampDown, target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_duration: [`p(95)<${CONFIG.thresholds.p95Ms}`],
    transport_error_rate: [`rate<${CONFIG.thresholds.transportErrorRate}`],
    api_response_contract_failure_rate: [`rate<${CONFIG.thresholds.contractFailureRate}`],
    api_expected_result_failure_rate: [`rate<${CONFIG.thresholds.expectedResultFailureRate}`],
    negative_inventory_observed: ["rate==0"],
  },
};

let session = null;

export function setup() {
  validateConfig();
  const setupSession = login("setup login");
  const productId = CONFIG.productId || discoverProductId(setupSession);
  if (!productId) {
    fail("PRODUCT_ID is not set and no product was found from the configured product list query");
  }
  return { productId };
}

export default function (data) {
  const auth = ensureSession();
  const productId = data.productId;

  requestWithAuth(
    "GET",
    CONFIG.paths.me,
    null,
    "user me",
    auth,
    expectSuccess()
  );

  const productListPath = withQuery(CONFIG.paths.productList, productListQuery());
  const productList = requestWithAuth(
    "GET",
    productListPath,
    null,
    "product list",
    auth,
    expectSuccess()
  );
  const listedProductId = productIdFromList(productList && productList.data);

  const activeProductId = productId || listedProductId;
  requestWithAuth(
    "GET",
    expandPath(CONFIG.paths.productDetail, { productId: activeProductId }),
    null,
    "product detail",
    auth,
    expectSuccess()
  );

  if (CONFIG.enableInventoryCheck) {
    checkInventory(activeProductId, auth);
  }

  if (CONFIG.enableOrderFlow && CONFIG.enableCancelFlow) {
    const cancelOrderNo = createOrder(activeProductId, "cancel", auth);
    if (cancelOrderNo) {
      requestWithAuth(
        "GET",
        expandPath(CONFIG.paths.orderDetail, { orderNo: cancelOrderNo }),
        null,
        "cancel order detail before cancel",
        auth,
        expectSuccess()
      );
      const cancelBody = requestWithAuth(
        "POST",
        expandPath(CONFIG.paths.orderCancel, { orderNo: cancelOrderNo }),
        null,
        "cancel order",
        auth,
        expectSuccessOrBusinessFailure([
          ERROR_CODES.CONFLICT,
          ERROR_CODES.NOT_FOUND,
          ERROR_CODES.BAD_REQUEST,
        ])
      );
      if (isSuccessful(cancelBody)) {
        orderCancelSuccesses.add(1);
      }
      requestWithAuth(
        "GET",
        expandPath(CONFIG.paths.orderDetail, { orderNo: cancelOrderNo }),
        null,
        "cancel order detail after cancel",
        auth,
        expectSuccessOrBusinessFailure([ERROR_CODES.NOT_FOUND])
      );
    }
  }

  if (CONFIG.enableOrderFlow && CONFIG.enablePaymentFlow) {
    const paymentOrderNo = createOrder(activeProductId, "payment", auth);
    if (paymentOrderNo) {
      requestWithAuth(
        "GET",
        expandPath(CONFIG.paths.orderDetail, { orderNo: paymentOrderNo }),
        null,
        "payment order detail",
        auth,
        expectSuccess()
      );
      const payBody = payWithRetry(paymentOrderNo, auth);
      if (isSuccessful(payBody)) {
        paymentSuccesses.add(1);
        requestWithAuth(
          "GET",
          expandPath(CONFIG.paths.paymentDetail, { orderNo: paymentOrderNo }),
          null,
          "payment detail",
          auth,
          expectSuccessOrBusinessFailure([ERROR_CODES.NOT_FOUND])
        );
      }
    }
  }

  sleep(CONFIG.sleepSeconds);
}

export function handleSummary(data) {
  const summary = {
    qps: metricValue(data, "http_reqs", "rate"),
    averageResponseMs: metricValue(data, "http_req_duration", "avg"),
    p95ResponseMs: metricValue(data, "http_req_duration", "p(95)"),
    transportErrorRate: metricValue(data, "transport_error_rate", "rate"),
    httpReqFailedRate: metricValue(data, "http_req_failed", "rate"),
    apiBusinessFailureRate: metricValue(data, "api_business_failure_rate", "rate"),
    apiContractFailureRate: metricValue(data, "api_response_contract_failure_rate", "rate"),
    apiExpectedResultFailureRate: metricValue(data, "api_expected_result_failure_rate", "rate"),
    negativeInventoryObservedRate: metricValue(data, "negative_inventory_observed", "rate"),
    totalRequests: metricValue(data, "http_reqs", "count"),
    iterations: metricValue(data, "iterations", "count"),
    orderCreateSuccesses: metricValue(data, "order_create_successes", "count"),
    orderCancelSuccesses: metricValue(data, "order_cancel_successes", "count"),
    paymentSuccesses: metricValue(data, "payment_successes", "count"),
    reloginAttempts: metricValue(data, "relogin_attempts", "count"),
  };

  return {
    stdout: `MiniMall Gateway pressure summary\n${JSON.stringify(summary, null, 2)}\n`,
  };
}

function validateConfig() {
  if (!CONFIG.baseUrl) {
    fail("BASE_URL is required and must point to api-gateway");
  }
  if (!CONFIG.username) {
    fail("USERNAME is required");
  }
  if (!CONFIG.password) {
    fail("PASSWORD is required");
  }
}

function ensureSession() {
  if (!session || !session.token) {
    session = login(`vu ${__VU} login`);
  }
  return session;
}

function login(label) {
  const response = postJson(CONFIG.paths.login, {
    username: CONFIG.username,
    password: CONFIG.password,
  }, null, label);
  const body = evaluateApiResponse(response, label, expectSuccess());
  const token = body && body.data && body.data.token;
  const tokenType = (body && body.data && body.data.tokenType) || "Bearer";
  if (!token) {
    fail(`${label}: login response did not include data.token`);
  }
  return { token, tokenType };
}

function discoverProductId(auth) {
  const response = requestWithAuth(
    "GET",
    withQuery(CONFIG.paths.productList, productListQuery()),
    null,
    "setup product list",
    auth,
    expectSuccess()
  );
  return productIdFromList(response && response.data);
}

function createOrder(productId, flowName, auth) {
  const body = requestWithAuth(
    "POST",
    CONFIG.paths.orderCreate,
    {
      productId,
      quantity: CONFIG.quantity,
      idempotencyKey: idempotencyKey(`order-${flowName}`),
    },
    `create ${flowName} order`,
    auth,
    expectSuccessOrBusinessFailure([
      ERROR_CODES.CONFLICT,
      ERROR_CODES.NOT_FOUND,
      ERROR_CODES.BAD_REQUEST,
      ERROR_CODES.VALIDATION_ERROR,
    ])
  );

  if (isSuccessful(body) && body.data && body.data.orderNo) {
    orderCreateSuccesses.add(1);
    return body.data.orderNo;
  }
  return null;
}

function payWithRetry(orderNo, auth) {
  let lastBody = null;
  const paymentIdempotencyKey = idempotencyKey("payment");
  for (let attempt = 1; attempt <= CONFIG.paymentRetryAttempts; attempt += 1) {
    lastBody = requestWithAuth(
      "POST",
      expandPath(CONFIG.paths.paymentPay, { orderNo }),
      {
        channel: CONFIG.paymentChannel,
        idempotencyKey: paymentIdempotencyKey,
      },
      `pay order attempt ${attempt}`,
      auth,
      expectSuccessOrBusinessFailure([
        ERROR_CODES.NOT_FOUND,
        ERROR_CODES.ORDER_CANCELLED,
        ERROR_CODES.ORDER_INVALID_STATE,
        ERROR_CODES.PAYMENT_ALREADY_SUCCESS,
        ERROR_CODES.CONFLICT,
      ])
    );
    if (!lastBody || lastBody.code !== ERROR_CODES.NOT_FOUND) {
      return lastBody;
    }
    sleep(CONFIG.paymentRetrySleepSeconds);
  }
  return lastBody;
}

function checkInventory(productId, auth) {
  const body = requestWithAuth(
    "GET",
    expandPath(CONFIG.paths.inventoryDetail, { productId }),
    null,
    "inventory detail",
    auth,
    expectSuccessOrBusinessFailure([ERROR_CODES.NOT_FOUND])
  );

  if (!isSuccessful(body) || !body.data) {
    return;
  }
  const availableStock = Number(body.data.availableStock);
  const lockedStock = Number(body.data.lockedStock);
  const isNegative = availableStock < 0 || lockedStock < 0;
  negativeInventoryObserved.add(isNegative);
  check(body.data, {
    "inventory stock is not negative": () => !isNegative,
  });
}

function requestWithAuth(method, path, payload, label, auth, expectation) {
  let response = send(method, path, payload, auth, label);
  let body = evaluateApiResponse(response, label, expectation);

  if (isUnauthorized(body)) {
    reloginAttempts.add(1);
    session = login(`${label} relogin`);
    auth.token = session.token;
    auth.tokenType = session.tokenType;
    response = send(method, path, payload, auth, `${label} retry`);
    body = evaluateApiResponse(response, `${label} retry`, expectation);
  }

  return body;
}

function send(method, path, payload, auth, label) {
  const params = {
    headers: requestHeaders(auth),
    tags: { api_name: label },
  };
  const url = CONFIG.baseUrl + path;
  if (method === "GET") {
    return http.get(url, params);
  }
  if (method === "POST") {
    return http.post(url, payload == null ? null : JSON.stringify(payload), params);
  }
  fail(`Unsupported method: ${method}`);
  return null;
}

function postJson(path, payload, auth, label) {
  return send("POST", path, payload, auth, label);
}

function requestHeaders(auth) {
  const headers = {
    Accept: "application/json",
    "Content-Type": "application/json",
  };
  if (auth && auth.token) {
    headers.Authorization = `${auth.tokenType || "Bearer"} ${auth.token}`;
  }
  return headers;
}

function evaluateApiResponse(response, label, expectation) {
  transportErrorRate.add(!(response && response.status > 0 && response.status < 500));
  check(response, {
    [`${label}: status is below 500`]: (res) => res && res.status > 0 && res.status < 500,
  });

  const body = parseJson(response, label);
  const hasApiResponseShape = isApiResponse(body);
  apiResponseContractFailureRate.add(!hasApiResponseShape);
  check(body, {
    [`${label}: has ApiResponse shape`]: () => hasApiResponseShape,
  });

  if (!hasApiResponseShape) {
    apiExpectedResultFailureRate.add(true);
    return body;
  }

  apiBusinessFailureRate.add(body.success === false);
  const accepted = expectation.accept(body);
  apiExpectedResultFailureRate.add(!accepted);
  check(body, {
    [`${label}: matches expected ApiResponse result`]: () => accepted,
  });
  return body;
}

function parseJson(response, label) {
  try {
    return response.json();
  } catch (error) {
    console.error(`${label}: response is not JSON`);
    return null;
  }
}

function expectSuccess() {
  return {
    accept: (body) => body.success === true && body.code === SUCCESS_CODE,
  };
}

function expectSuccessOrBusinessFailure(allowedFailureCodes) {
  return {
    accept: (body) => {
      if (body.success === true && body.code === SUCCESS_CODE) {
        return true;
      }
      return body.success === false && allowedFailureCodes.indexOf(body.code) >= 0;
    },
  };
}

function isApiResponse(value) {
  return value
    && typeof value.success === "boolean"
    && typeof value.code === "string"
    && typeof value.message === "string";
}

function isSuccessful(body) {
  return isApiResponse(body) && body.success === true && body.code === SUCCESS_CODE;
}

function isUnauthorized(body) {
  return isApiResponse(body) && body.success === false && body.code === ERROR_CODES.UNAUTHORIZED;
}

function productListQuery() {
  return {
    status: CONFIG.productStatus,
    page: CONFIG.productPage,
    size: CONFIG.productSize,
  };
}

function productIdFromList(pageData) {
  if (!pageData || !Array.isArray(pageData.content) || pageData.content.length === 0) {
    return null;
  }
  const first = pageData.content[0];
  return first && first.productId ? first.productId : null;
}

function idempotencyKey(prefix) {
  const runId = __ENV.RUN_ID || Date.now();
  return `${prefix}-${runId}-vu${__VU}-iter${__ITER}`;
}

function withQuery(path, params) {
  const query = Object.keys(params)
    .filter((key) => params[key] !== null && params[key] !== undefined && params[key] !== "")
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join("&");
  return query ? `${path}?${query}` : path;
}

function expandPath(path, values) {
  return Object.keys(values).reduce(
    (expanded, key) => expanded.replace(`{${key}}`, encodeURIComponent(values[key])),
    path
  );
}

function normalizeBaseUrl(value) {
  const normalized = blankToNull(value);
  if (!normalized) {
    return null;
  }
  return normalized.endsWith("/") ? normalized.slice(0, -1) : normalized;
}

function blankToNull(value) {
  if (value === undefined || value === null) {
    return null;
  }
  const trimmed = String(value).trim();
  return trimmed.length === 0 ? null : trimmed;
}

function booleanEnv(value, defaultValue) {
  const normalized = blankToNull(value);
  if (normalized === null) {
    return defaultValue;
  }
  return !["0", "false", "no", "off"].includes(normalized.toLowerCase());
}

function positiveInt(value, defaultValue) {
  const parsed = parseInt(blankToNull(value) || defaultValue, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : defaultValue;
}

function nonNegativeInt(value, defaultValue) {
  const parsed = parseInt(blankToNull(value) || defaultValue, 10);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : defaultValue;
}

function positiveNumber(value, defaultValue) {
  const parsed = Number(blankToNull(value) || defaultValue);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : defaultValue;
}

function thresholdValue(value, defaultValue) {
  const parsed = Number(blankToNull(value) || defaultValue);
  return Number.isFinite(parsed) && parsed >= 0 ? String(parsed) : defaultValue;
}

function metricValue(data, metricName, valueName) {
  const metric = data.metrics[metricName];
  if (!metric || !metric.values || metric.values[valueName] === undefined) {
    return null;
  }
  return metric.values[valueName];
}
