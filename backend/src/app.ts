import express from "express";

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

  return app;
}
