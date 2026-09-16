import { createHmac, timingSafeEqual } from "node:crypto";

const TOKEN_VERSION = "v1";
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 30;

type SessionClaims = {
  sub: string;
  exp: number;
};

function encode(value: string): string {
  return Buffer.from(value, "utf8").toString("base64url");
}

function sign(value: string, secret: string): string {
  return createHmac("sha256", secret).update(value).digest("base64url");
}

export function createSessionToken(userId: string, secret: string, nowSeconds = Math.floor(Date.now() / 1000)): string {
  const payload = encode(JSON.stringify({ sub: userId, exp: nowSeconds + SESSION_TTL_SECONDS } satisfies SessionClaims));
  const unsigned = `${TOKEN_VERSION}.${payload}`;
  return `${unsigned}.${sign(unsigned, secret)}`;
}

export function verifySessionToken(token: string, secret: string, nowSeconds = Math.floor(Date.now() / 1000)): string | null {
  const [version, payload, signature] = token.split(".");
  if (version !== TOKEN_VERSION || !payload || !signature) return null;

  const unsigned = `${version}.${payload}`;
  const expected = sign(unsigned, secret);
  const supplied = Buffer.from(signature);
  const expectedBuffer = Buffer.from(expected);
  if (supplied.length !== expectedBuffer.length || !timingSafeEqual(supplied, expectedBuffer)) return null;

  try {
    const claims = JSON.parse(Buffer.from(payload, "base64url").toString("utf8")) as SessionClaims;
    if (!claims.sub || !Number.isSafeInteger(claims.exp) || claims.exp <= nowSeconds) return null;
    return claims.sub;
  } catch {
    return null;
  }
}
