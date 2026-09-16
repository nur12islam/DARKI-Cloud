import { createServer } from "node:http";
import { createApp } from "./app.js";
import { config } from "./config.js";

const app = createApp();
const server = createServer(app);

server.listen(config.port, config.host, () => {
  console.log(`DARKI Cloud backend listening on ${config.host}:${config.port}`);
});

function shutdown(signal: string) {
  console.log(`${signal} received; shutting down`);
  server.close((error) => {
    if (error) {
      console.error(error);
      process.exitCode = 1;
    }
    process.exit();
  });
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
