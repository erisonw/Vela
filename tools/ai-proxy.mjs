#!/usr/bin/env node
import http from "node:http";

const DEFAULT_PORT = 8787;
const LOCAL_HOST = "127.0.0.1";
const JSON_CONTENT_TYPE = "application/json; charset=utf-8";
const MAX_UPSTREAM_ATTEMPTS = 3;
const RETRYABLE_UPSTREAM_STATUS = new Set([502, 503, 504]);

export function readProxyConfig(env = process.env) {
  return {
    baseUrl: (env.VELA_AI_BASE_URL ?? "").trim().replace(/\/+$/, ""),
    apiKey: (env.VELA_AI_API_KEY ?? "").trim(),
    textModel: (env.VELA_AI_TEXT_MODEL ?? "").trim(),
    visionModel: (env.VELA_AI_VISION_MODEL ?? "").trim(),
    voiceModel: (env.VELA_AI_VOICE_MODEL ?? "").trim(),
    port: Number.parseInt(env.VELA_AI_PROXY_PORT ?? `${DEFAULT_PORT}`, 10) || DEFAULT_PORT,
  };
}

export function buildUpstreamUrl(config, routePath) {
  const cleanRoute = routePath.startsWith("/") ? routePath : `/${routePath}`;
  if (config.baseUrl.endsWith("/v1") && cleanRoute.startsWith("/v1/")) {
    return `${config.baseUrl}${cleanRoute.slice(3)}`;
  }
  return `${config.baseUrl}${cleanRoute}`;
}

export function proxyModelFor(config, requestedModel) {
  const cleanModel = `${requestedModel ?? ""}`.trim();
  switch (cleanModel) {
    case "proxy-text":
      return config.textModel;
    case "proxy-vision":
      return config.visionModel;
    case "proxy-voice":
      return config.voiceModel;
    default:
      return cleanModel;
  }
}

export function sanitizeLogText(text) {
  return `${text ?? ""}`
    .replace(/Bearer\s+[^\s"'{}]+/gi, "Bearer [REDACTED]")
    .replace(/sk-[A-Za-z0-9_-]+/g, "sk-[REDACTED]")
    .replace(/data:[^;,]+;base64,[A-Za-z0-9+/=_-]+/g, "data:[REDACTED];base64,[REDACTED]");
}

export function createProxyServer(config = readProxyConfig()) {
  return http.createServer(async (request, response) => {
    const startedAt = Date.now();
    const route = new URL(request.url ?? "/", "http://localhost").pathname;

    try {
      if (request.method === "GET" && route === "/health") {
        sendJson(response, 200, healthPayload(config));
        logRequest(route, 200, startedAt);
        return;
      }
      if (request.method !== "POST" || !isSupportedRoute(route)) {
        sendJson(response, 404, { error: "NOT_FOUND", message: "Unknown Vela AI proxy route." });
        logRequest(route, 404, startedAt);
        return;
      }
      const missing = missingConfig(config);
      if (missing.length > 0) {
        sendJson(response, 502, {
          error: "CONFIG_MISSING",
          message: `Missing environment: ${missing.join(", ")}`,
        });
        logRequest(route, 502, startedAt, "CONFIG_MISSING");
        return;
      }

      const incomingBody = await readRequestBody(request);
      const prepared = route === "/v1/chat/completions"
        ? prepareChatCompletionsBody(config, incomingBody)
        : prepareAudioTranscriptionsBody(config, incomingBody);
      if (prepared.error) {
        sendJson(response, 502, prepared.error);
        logRequest(route, 502, startedAt, prepared.error.error);
        return;
      }

      const upstreamResponse = await fetchUpstreamWithRetry(route, startedAt, {
        url: buildUpstreamUrl(config, route),
        method: "POST",
        headers: upstreamHeaders(config, request, prepared.contentType),
        body: prepared.body,
      });
      const upstreamBody = Buffer.from(await upstreamResponse.arrayBuffer());
      response.writeHead(upstreamResponse.status, responseHeaders(upstreamResponse));
      response.end(upstreamBody);
      logRequest(route, upstreamResponse.status, startedAt);
    } catch (error) {
      sendJson(response, 502, {
        error: "UPSTREAM_ERROR",
        message: sanitizeLogText(error?.message ?? "AI proxy request failed."),
      });
      logRequest(route, 502, startedAt, error?.message);
    }
  });
}

export function shouldRetryUpstream({ attempt, statusCode, error }) {
  if (attempt >= MAX_UPSTREAM_ATTEMPTS) return false;
  if (error != null) return true;
  return RETRYABLE_UPSTREAM_STATUS.has(statusCode);
}

async function fetchUpstreamWithRetry(route, startedAt, requestInit) {
  let lastError = null;
  for (let attempt = 1; attempt <= MAX_UPSTREAM_ATTEMPTS; attempt += 1) {
    try {
      const response = await fetch(requestInit.url, requestInit);
      if (!shouldRetryUpstream({ attempt, statusCode: response.status })) {
        return response;
      }
      lastError = new Error(`upstream ${response.status}`);
      logRequest(route, response.status, startedAt, `retry ${attempt}/${MAX_UPSTREAM_ATTEMPTS}`);
      await sleep(retryDelayMs(attempt));
    } catch (error) {
      if (!shouldRetryUpstream({ attempt, error })) {
        throw error;
      }
      lastError = error;
      logRequest(route, 502, startedAt, `retry ${attempt}/${MAX_UPSTREAM_ATTEMPTS}: ${error?.message ?? "fetch failed"}`);
      await sleep(retryDelayMs(attempt));
    }
  }
  throw lastError ?? new Error("upstream failed");
}

function retryDelayMs(attempt) {
  return 450 * attempt;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isSupportedRoute(route) {
  return route === "/v1/chat/completions" || route === "/v1/audio/transcriptions";
}

function missingConfig(config) {
  return [
    ["VELA_AI_BASE_URL", config.baseUrl],
    ["VELA_AI_API_KEY", config.apiKey],
  ].filter(([, value]) => !value).map(([name]) => name);
}

function healthPayload(config) {
  const missing = missingConfig(config);
  return {
    ok: true,
    configured: missing.length === 0,
    missing,
    routes: ["/v1/chat/completions", "/v1/audio/transcriptions"],
  };
}

function prepareChatCompletionsBody(config, body) {
  try {
    const payload = JSON.parse(body.toString("utf8"));
    const resolvedModel = proxyModelFor(config, payload.model);
    if (!resolvedModel) {
      return { error: { error: "MODEL_MISSING", message: `Missing upstream model for ${payload.model}.` } };
    }
    payload.model = resolvedModel;
    return {
      body: JSON.stringify(payload),
      contentType: JSON_CONTENT_TYPE,
    };
  } catch {
    return { error: { error: "INVALID_JSON", message: "Chat completions body is not valid JSON." } };
  }
}

function prepareAudioTranscriptionsBody(config, body) {
  const requestedModel = readMultipartField(body, "model") ?? "proxy-voice";
  const resolvedModel = proxyModelFor(config, requestedModel);
  if (!resolvedModel) {
    return { error: { error: "MODEL_MISSING", message: `Missing upstream model for ${requestedModel}.` } };
  }
  return {
    body: replaceMultipartField(body, "model", resolvedModel),
    contentType: null,
  };
}

function readMultipartField(body, fieldName) {
  const marker = Buffer.from(`Content-Disposition: form-data; name="${fieldName}"`);
  const markerIndex = body.indexOf(marker);
  if (markerIndex < 0) return null;
  const valuePrefix = Buffer.from("\r\n\r\n");
  const valueStartPrefix = body.indexOf(valuePrefix, markerIndex + marker.length);
  if (valueStartPrefix < 0) return null;
  const valueStart = valueStartPrefix + valuePrefix.length;
  const valueEnd = body.indexOf(Buffer.from("\r\n"), valueStart);
  if (valueEnd < 0) return null;
  return body.subarray(valueStart, valueEnd).toString("utf8");
}

function replaceMultipartField(body, fieldName, value) {
  const marker = Buffer.from(`Content-Disposition: form-data; name="${fieldName}"`);
  const markerIndex = body.indexOf(marker);
  if (markerIndex < 0) return body;
  const valuePrefix = Buffer.from("\r\n\r\n");
  const valueStartPrefix = body.indexOf(valuePrefix, markerIndex + marker.length);
  if (valueStartPrefix < 0) return body;
  const valueStart = valueStartPrefix + valuePrefix.length;
  const valueEnd = body.indexOf(Buffer.from("\r\n"), valueStart);
  if (valueEnd < 0) return body;
  return Buffer.concat([
    body.subarray(0, valueStart),
    Buffer.from(value),
    body.subarray(valueEnd),
  ]);
}

function upstreamHeaders(config, request, preparedContentType) {
  const headers = {
    Authorization: `Bearer ${config.apiKey}`,
  };
  const contentType = preparedContentType ?? request.headers["content-type"];
  if (contentType) headers["Content-Type"] = contentType;
  return headers;
}

function responseHeaders(upstreamResponse) {
  const headers = {};
  const contentType = upstreamResponse.headers.get("content-type");
  if (contentType) headers["Content-Type"] = contentType;
  return headers;
}

function readRequestBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(Buffer.from(chunk)));
    request.on("end", () => resolve(Buffer.concat(chunks)));
    request.on("error", reject);
  });
}

function sendJson(response, statusCode, payload) {
  response.writeHead(statusCode, { "Content-Type": JSON_CONTENT_TYPE });
  response.end(JSON.stringify(payload));
}

function logRequest(route, statusCode, startedAt, errorText = "") {
  const durationMs = Date.now() - startedAt;
  const cleanError = sanitizeLogText(errorText);
  const errorPart = cleanError ? ` error="${cleanError}"` : "";
  console.log(`[vela-ai-proxy] route=${route} status=${statusCode} durationMs=${durationMs}${errorPart}`);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const config = readProxyConfig();
  createProxyServer(config).listen(config.port, LOCAL_HOST, () => {
    console.log(`[vela-ai-proxy] listening=http://${LOCAL_HOST}:${config.port}`);
    console.log(`[vela-ai-proxy] health=http://${LOCAL_HOST}:${config.port}/health`);
  });
}
