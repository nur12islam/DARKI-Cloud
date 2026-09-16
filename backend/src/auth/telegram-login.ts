import { createHash, createHmac, timingSafeEqual } from "node:crypto";
import type { TelegramLoginPayload } from "./types.js";

const MAX_AUTH_AGE_SECONDS = 300;

function buildCheckString(payload: TelegramLoginPayload): string {
  return Object.entries(payload)
    .filter(([key, value]) => key !== "hash" && value !== undefined && value !== null)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${key}=${value}`)
    .join("\n");
}

export function verifyTelegramLogin(
  payload: TelegramLoginPayload,
  botToken: string,
  nowSeconds = Math.floor(Date.now() / 1000),
): boolean {
  if (!/^\d+$/.test(payload.id) || !/^\d+$/.test(payload.auth_date)) {
    return false;
  }

  const authDate = Number(payload.auth_date);
  if (!Number.isSafeInteger(authDate) || nowSeconds - authDate < 0 || nowSeconds - authDate > MAX_AUTH_AGE_SECONDS) {
    return false;
  }

  const secretKey = createHash("sha256").update(botToken).digest();
  const expected = createHmac("sha256", secretKey)
    .update(buildCheckString(payload))
    .digest();

  const supplied = Buffer.from(payload.hash, "hex");
  return supplied.length === expected.length && timingSafeEqual(supplied, expected);
}

export function displayNameFromTelegram(payload: TelegramLoginPayload): string {
  return [payload.first_name, payload.last_name].filter(Boolean).join(" ").trim()
    || payload.username
    || `Telegram ${payload.id}`;
}
