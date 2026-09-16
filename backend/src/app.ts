import express from "express";
import { ServiceError } from "./services/errors.js";
import { createApiRouter } from "./routes/api.js";

export function createApp() {
  const app = express();

  app.disable("x-powered-by");
  app.use(express.json({ limit: "1mb" }));

  app.get("/health", (_req, res) => {
    res.status(200).json({
      status: "ok",
      service: "darki-cloud-backend",
    });
  });

  app.get("/api/v1/health", (_req, res) => {
    res.status(200).json({
      status: "ok",
      service: "darki-cloud-api",
    });
  });

  app.use("/api/v1", createApiRouter());

  app.use((error: unknown, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
    if (error instanceof ServiceError) {
      res.status(error.statusCode).json({
        error: { code: error.code, message: error.message },
      });
      return;
    }

    console.error("Unhandled request error", error);
    res.status(500).json({
      error: { code: "INTERNAL_ERROR", message: "Internal server error" },
    });
  });

  return app;
}
