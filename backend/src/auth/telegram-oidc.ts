import { createHash, randomBytes } from "node:crypto";
import { createRemoteJWKSet, jwtVerify } from "jose";
import { db } from "../db/pool.js";
import { withTransaction } from "../db/transaction.js";
import { UserRepository } from "../db/repositories/user-repository.js";
import { FolderRepository } from "../db/repositories/folder-repository.js";
import { ServiceError } from "../services/errors.js";
import { AuthService } from "./auth-service.js";

const TELEGRAM_AUTH_URL = "https://oauth.telegram.org/auth";
const TELEGRAM_TOKEN_URL = "https://oauth.telegram.org/token";
const TELEGRAM_JWKS_URL = "https://oauth.telegram.org/.well-known/jwks.json";
const ISSUER = "https://oauth.telegram.org";
const ATTEMPT_TTL_MS = 10 * 60 * 1000;
const EXCHANGE_TTL_MS = 2 * 60 * 1000;

const jwks = createRemoteJWKSet(new URL(TELEGRAM_JWKS_URL));

function hash(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

function base64Url(bytes: Buffer): string {
  return bytes.toString("base64url");
}

function pkceChallenge(verifier: string): string {
  return base64Url(createHash("sha256").update(verifier, "utf8").digest());
}

export type TelegramOidcConfig = {
  clientId: string;
  clientSecret: string;
  redirectUri: string;
  appRedirectUri: string;
};

export class TelegramOidcService {
  constructor(
    private readonly config: TelegramOidcConfig,
    private readonly auth = new AuthService(),
  ) {}

  async start(): Promise<string> {
    const state = base64Url(randomBytes(32));
    const verifier = base64Url(randomBytes(32));
    await db.query(
      `INSERT INTO telegram_login_attempts (state_hash, code_verifier, expires_at)
       VALUES ($1, $2, $3)`,
      [hash(state), verifier, new Date(Date.now() + ATTEMPT_TTL_MS)],
    );

    const url = new URL(TELEGRAM_AUTH_URL);
    url.searchParams.set("client_id", this.config.clientId);
    url.searchParams.set("redirect_uri", this.config.redirectUri);
    url.searchParams.set("response_type", "code");
    url.searchParams.set("scope", "openid profile");
    url.searchParams.set("state", state);
    url.searchParams.set("code_challenge", pkceChallenge(verifier));
    url.searchParams.set("code_challenge_method", "S256");
    return url.toString();
  }

  async callback(code: string, state: string): Promise<string> {
    const result = await db.query<{ id: string; codeVerifier: string }>(
      `SELECT id, code_verifier AS "codeVerifier"
         FROM telegram_login_attempts
        WHERE state_hash = $1 AND used_at IS NULL AND expires_at > now()`,
      [hash(state)],
    );
    const attempt = result.rows[0];
    if (!attempt) throw new ServiceError("Telegram login session expired", "INVALID_AUTH", 401);

    const tokenResponse = await fetch(TELEGRAM_TOKEN_URL, {
      method: "POST",
      headers: {
        "content-type": "application/x-www-form-urlencoded",
        authorization: `Basic ${Buffer.from(`${this.config.clientId}:${this.config.clientSecret}`).toString("base64")}`,
      },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        code,
        redirect_uri: this.config.redirectUri,
        client_id: this.config.clientId,
        code_verifier: attempt.codeVerifier,
      }),
    });
    if (!tokenResponse.ok) throw new ServiceError("Telegram token exchange failed", "INVALID_AUTH", 401);

    const tokens = await tokenResponse.json() as { id_token?: string };
    if (!tokens.id_token) throw new ServiceError("Telegram did not return an ID token", "INVALID_AUTH", 401);

    const { payload } = await jwtVerify(tokens.id_token, jwks, {
      issuer: ISSUER,
      audience: this.config.clientId,
    });
    if (typeof payload.sub !== "string" || !/^\d+$/.test(payload.sub)) {
      throw new ServiceError("Telegram identity is invalid", "INVALID_AUTH", 401);
    }

    const claims = payload as typeof payload & {
      name?: string;
      given_name?: string;
      family_name?: string;
      preferred_username?: string;
    };
    const displayName = claims.name?.trim() || [claims.given_name, claims.family_name].filter(Boolean).join(" ").trim() || claims.preferred_username || `Telegram ${payload.sub}`;

    const user = await withTransaction(async (client) => {
      const users = new UserRepository(client);
      const folders = new FolderRepository(client);
      const existing = await users.findByTelegramUserId(payload.sub!);
      if (existing) return existing;
      const created = await users.create(payload.sub!, displayName);
      await folders.create(created.id, null, "My Drive");
      return created;
    });

    const exchangeCode = base64Url(randomBytes(32));
    await db.query(
      `UPDATE telegram_login_attempts
          SET used_at = now(), user_id = $1, exchange_code_hash = $2,
              expires_at = $3
        WHERE id = $4 AND used_at IS NULL`,
      [user.id, hash(exchangeCode), new Date(Date.now() + EXCHANGE_TTL_MS), attempt.id],
    );
    return `${this.config.appRedirectUri}?code=${encodeURIComponent(exchangeCode)}`;
  }

  async exchange(code: string): Promise<{ token: string; user: Awaited<ReturnType<AuthService["authenticateUser"]>> }> {
    return withTransaction(async (client) => {
      const result = await client.query<{ id: string; userId: string }>(
        `SELECT id, user_id AS "userId"
           FROM telegram_login_attempts
          WHERE exchange_code_hash = $1 AND used_at IS NOT NULL AND expires_at > now()
          FOR UPDATE`,
        [hash(code)],
      );
      const attempt = result.rows[0];
      if (!attempt) throw new ServiceError("Invalid or expired login code", "INVALID_AUTH", 401);

      await client.query(`UPDATE telegram_login_attempts SET exchange_code_hash = NULL, expires_at = now() WHERE id = $1`, [attempt.id]);
      const user = await new UserRepository(client).findById(attempt.userId);
      if (!user) throw new ServiceError("User not found", "INVALID_AUTH", 401);
      const token = await this.auth.createSession(user.id);
      return { token, user };
    });
  }
}
