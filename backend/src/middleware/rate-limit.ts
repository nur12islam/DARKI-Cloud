import type { NextFunction, Request, Response } from "express";

type Bucket = { startedAt: number; count: number };

export type RateLimitOptions = {
  windowMs: number;
  max: number;
  key: (req: Request) => string;
};

export function createRateLimiter(options: RateLimitOptions) {
  const buckets = new Map<string, Bucket>();

  return (req: Request, res: Response, next: NextFunction): void => {
    const now = Date.now();
    const key = options.key(req);
    const current = buckets.get(key);

    if (!current || now - current.startedAt >= options.windowMs) {
      buckets.set(key, { startedAt: now, count: 1 });
      next();
      return;
    }

    current.count += 1;
    if (current.count <= options.max) {
      next();
      return;
    }

    const retryAfterSeconds = Math.max(1, Math.ceil((current.startedAt + options.windowMs - now) / 1000));
    res.setHeader("Retry-After", retryAfterSeconds);
    res.status(429).json({
      error: {
        code: "RATE_LIMITED",
        message: "Too many requests; please try again later",
      },
    });
  };
}

export function clientAddress(req: Request): string {
  return req.ip || req.socket.remoteAddress || "unknown";
}
