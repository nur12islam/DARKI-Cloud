import { Router } from "express";
import { requireTelegramBotToken, requireTelegramOidcConfig } from "../config.js";
import { AuthService } from "../auth/auth-service.js";
import { TelegramOidcService } from "../auth/telegram-oidc.js";
import { requireAuth, type AuthenticatedRequest } from "../auth/middleware.js";
import type { TelegramLoginPayload } from "../auth/types.js";
import { FilesystemService } from "../services/filesystem-service.js";
import { FileService } from "../services/file-service.js";
import { FileLifecycleService } from "../services/file-lifecycle-service.js";
import { SyncService } from "../services/sync-service.js";
import { getStorageProvider } from "../storage/provider.js";

const authService = new AuthService();
const telegramOidc = new TelegramOidcService(requireTelegramOidcConfig(), authService);
const filesystemService = new FilesystemService();
const fileService = new FileService(getStorageProvider());
const fileLifecycleService = new FileLifecycleService(getStorageProvider());
const syncService = new SyncService();

export function createApiRouter(): Router {
  const router = Router();

  router.get("/auth/telegram/start", async (_req, res, next) => {
    try { res.redirect(302, await telegramOidc.start()); } catch (error) { next(error); }
  });
  router.get("/auth/telegram/callback", async (req, res, next) => {
    try {
      const code = typeof req.query.code === "string" ? req.query.code : "";
      const state = typeof req.query.state === "string" ? req.query.state : "";
      if (!code || !state) { res.status(400).send("Invalid Telegram login response"); return; }
      res.redirect(302, await telegramOidc.callback(code, state));
    } catch (error) { next(error); }
  });
  router.post("/auth/telegram/exchange", async (req, res, next) => {
    try {
      const code = typeof req.body?.code === "string" ? req.body.code : "";
      if (!code) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "Login code is required" } }); return; }
      res.json(await telegramOidc.exchange(code));
    } catch (error) { next(error); }
  });
  router.post("/auth/telegram", async (req, res, next) => {
    try {
      const payload = req.body as TelegramLoginPayload;
      const user = await authService.loginWithTelegram(payload, requireTelegramBotToken());
      const token = await authService.createSession(user.id);
      res.status(200).json({ user, token, tokenType: "Bearer" });
    } catch (error) { next(error); }
  });
  router.post("/auth/logout", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { if (req.sessionToken) await authService.revokeSession(req.sessionToken); res.status(204).send(); }
    catch (error) { next(error); }
  });
  router.get("/me", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { res.json({ user: await authService.authenticateUser(req.userId!) }); } catch (error) { next(error); }
  });

  router.post("/devices", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { deviceName, platform } = req.body as { deviceName?: string; platform?: string };
      if (!deviceName?.trim() || !platform?.trim()) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "deviceName and platform are required" } }); return; }
      res.status(201).json({ device: await filesystemService.registerDevice(req.userId!, deviceName, platform) });
    } catch (error) { next(error); }
  });
  router.get("/sync/pull", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const deviceId = typeof req.query.deviceId === "string" ? req.query.deviceId : "";
      const cursor = typeof req.query.cursor === "string" ? req.query.cursor : "0";
      const limit = typeof req.query.limit === "string" ? Number(req.query.limit) : 100;
      if (!deviceId || !Number.isSafeInteger(limit) || limit < 1 || limit > 500) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "deviceId and limit (1-500) are required" } }); return; }
      res.json(await syncService.pull(req.userId!, deviceId, cursor, limit));
    } catch (error) { next(error); }
  });
  router.post("/sync/ack", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { deviceId, cursor } = req.body as { deviceId?: string; cursor?: string };
      if (!deviceId || typeof cursor !== "string") { res.status(400).json({ error: { code: "INVALID_INPUT", message: "deviceId and cursor are required" } }); return; }
      await syncService.acknowledge(req.userId!, deviceId, cursor);
      res.status(204).send();
    } catch (error) { next(error); }
  });

  router.get("/search", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const query = typeof req.query.q === "string" ? req.query.q : "";
      const limit = typeof req.query.limit === "string" ? Number(req.query.limit) : 50;
      if (!query.trim() || !Number.isSafeInteger(limit) || limit < 1 || limit > 100) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "q and limit (1-100) are required" } }); return; }
      res.json(await filesystemService.search(req.userId!, query, limit));
    } catch (error) { next(error); }
  });

  router.get("/storage/capabilities", requireAuth, async (_req, res, next) => {
    try { res.json({ provider: fileService.storageName, capabilities: await fileService.capabilities() }); } catch (error) { next(error); }
  });
  router.get("/storage/health", requireAuth, async (_req, res, next) => {
    try { res.json(await fileService.healthCheck()); } catch (error) { next(error); }
  });
  router.get("/folders/root", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { res.json({ folder: await filesystemService.getRootFolder(req.userId!) }); } catch (error) { next(error); }
  });
  router.get("/folders/:folderId", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { res.json(await filesystemService.listFolder(req.userId!, req.params.folderId)); } catch (error) { next(error); }
  });
  router.post("/folders", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { parentId, name, deviceId } = req.body as { parentId?: string; name?: string; deviceId?: string };
      if (!parentId || !name) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "parentId and name are required" } }); return; }
      res.status(201).json({ folder: await filesystemService.createFolder(req.userId!, parentId, name, deviceId) });
    } catch (error) { next(error); }
  });
  router.patch("/folders/:folderId", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { name, parentId, deviceId } = req.body as { name?: string; parentId?: string; deviceId?: string };
      if (typeof name === "string" && parentId) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "Choose either name or parentId" } }); return; }
      if (typeof name === "string") { res.json({ folder: await filesystemService.renameFolder(req.userId!, req.params.folderId, name, deviceId) }); return; }
      if (typeof parentId === "string") { res.json({ folder: await filesystemService.moveFolder(req.userId!, req.params.folderId, parentId, deviceId) }); return; }
      res.status(400).json({ error: { code: "INVALID_INPUT", message: "name or parentId is required" } });
    } catch (error) { next(error); }
  });
  router.post("/files", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const folderId = typeof req.query.folderId === "string" ? req.query.folderId : undefined;
      const name = typeof req.query.name === "string" ? req.query.name : undefined;
      const mimeType = req.header("content-type")?.split(";", 1)[0]?.trim() || null;
      const lengthHeader = req.header("content-length");
      const sizeBytes = lengthHeader ? Number(lengthHeader) : NaN;
      const operationId = req.header("x-operation-id")?.trim() || undefined;
      if (!folderId || !name || !Number.isSafeInteger(sizeBytes) || sizeBytes < 0) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "folderId, name, and a valid Content-Length are required" } }); return; }
      res.status(201).json({ file: await fileService.upload({ userId: req.userId!, folderId, name, mimeType, sizeBytes, body: req, deviceId: req.header("x-device-id"), operationId }) });
    } catch (error) { next(error); }
  });
  router.patch("/files/:fileId", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const { name, folderId, deviceId } = req.body as { name?: string; folderId?: string; deviceId?: string };
      if (typeof name === "string" && folderId) { res.status(400).json({ error: { code: "INVALID_INPUT", message: "Choose either name or folderId" } }); return; }
      if (typeof name === "string") { res.json({ file: await filesystemService.renameFile(req.userId!, req.params.fileId, name, deviceId) }); return; }
      if (typeof folderId === "string") { res.json({ file: await filesystemService.moveFile(req.userId!, req.params.fileId, folderId, deviceId) }); return; }
      res.status(400).json({ error: { code: "INVALID_INPUT", message: "name or folderId is required" } });
    } catch (error) { next(error); }
  });
  router.get("/files/:fileId/content", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try {
      const result = await fileService.getDownload(req.userId!, req.params.fileId);
      res.setHeader("Content-Type", result.file.mimeType ?? "application/octet-stream");
      if (result.file.sizeBytes !== null) res.setHeader("Content-Length", result.file.sizeBytes);
      res.setHeader("Content-Disposition", `attachment; filename*=UTF-8''${encodeURIComponent(result.file.name)}`);
      result.body.pipe(res);
    } catch (error) { next(error); }
  });
  router.delete("/files/:fileId", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { res.json(await fileLifecycleService.delete(req.userId!, req.params.fileId, req.header("x-device-id"))); } catch (error) { next(error); }
  });
  router.post("/files/:fileId/restore", requireAuth, async (req: AuthenticatedRequest, res, next) => {
    try { res.json({ file: await fileLifecycleService.restore(req.userId!, req.params.fileId, req.header("x-device-id")) }); } catch (error) { next(error); }
  });
  return router;
}
