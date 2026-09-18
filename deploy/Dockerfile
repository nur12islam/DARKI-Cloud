FROM node:20-bookworm-slim AS build
WORKDIR /app/backend
COPY backend/package*.json ./
RUN npm install
COPY backend/ ./
COPY database/ /app/database/
RUN npm run build

FROM node:20-bookworm-slim
ENV NODE_ENV=production
WORKDIR /app
COPY --from=build /app/backend/package*.json /app/backend/
COPY --from=build /app/backend/node_modules /app/backend/node_modules
COPY --from=build /app/backend/dist /app/backend/dist
COPY --from=build /app/database /app/database
WORKDIR /app/backend
EXPOSE 8080
CMD ["sh", "-c", "npm run db:migrate && npm start"]
