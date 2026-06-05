import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  buildUpstreamUrl,
  proxyModelFor,
  readProxyConfig,
  sanitizeLogText,
  shouldRetryUpstream,
} from "./ai-proxy.mjs";

describe("ai-proxy helpers", () => {
  it("builds upstream v1 routes without duplicating path segments", () => {
    const config = readProxyConfig({
      VELA_AI_BASE_URL: "https://example.test/v1/",
      VELA_AI_API_KEY: "secret",
    });

    assert.equal(
      buildUpstreamUrl(config, "/v1/chat/completions"),
      "https://example.test/v1/chat/completions",
    );
    assert.equal(
      buildUpstreamUrl(config, "/v1/audio/transcriptions"),
      "https://example.test/v1/audio/transcriptions",
    );
  });

  it("replaces app proxy model aliases with configured upstream models", () => {
    const config = readProxyConfig({
      VELA_AI_BASE_URL: "https://example.test/v1",
      VELA_AI_API_KEY: "secret",
      VELA_AI_TEXT_MODEL: "gpt-text",
      VELA_AI_VISION_MODEL: "gpt-vision",
      VELA_AI_VOICE_MODEL: "gpt-voice",
    });

    assert.equal(proxyModelFor(config, "proxy-text"), "gpt-text");
    assert.equal(proxyModelFor(config, "proxy-vision"), "gpt-vision");
    assert.equal(proxyModelFor(config, "proxy-voice"), "gpt-voice");
    assert.equal(proxyModelFor(config, "custom-model"), "custom-model");
  });

  it("does not leak secrets or base64-like payloads in logs", () => {
    const text = sanitizeLogText("Bearer sk-real-secret data:image/jpeg;base64,abcdef1234567890");

    assert.equal(text.includes("sk-real-secret"), false);
    assert.equal(text.includes("abcdef1234567890"), false);
  });

  it("retries only transient upstream failures", () => {
    assert.equal(shouldRetryUpstream({ attempt: 1, statusCode: 502 }), true);
    assert.equal(shouldRetryUpstream({ attempt: 1, statusCode: 503 }), true);
    assert.equal(shouldRetryUpstream({ attempt: 1, statusCode: 504 }), true);
    assert.equal(shouldRetryUpstream({ attempt: 1, error: new Error("fetch failed") }), true);
    assert.equal(shouldRetryUpstream({ attempt: 3, statusCode: 502 }), false);
    assert.equal(shouldRetryUpstream({ attempt: 1, statusCode: 400 }), false);
    assert.equal(shouldRetryUpstream({ attempt: 1, statusCode: 429 }), false);
  });
});
