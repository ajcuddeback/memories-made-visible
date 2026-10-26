# syntax=docker/dockerfile:1.7
# ── Build ────────────────────────────────────────────────────────────────────
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ── Serve ────────────────────────────────────────────────────────────────────
FROM nginxinc/nginx-unprivileged:1.27-alpine
# nginx renders /etc/nginx/templates/*.template with envsubst at start-up
COPY docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
# Where the API lives (compose service, ECS sidecar, ALB…). Overridden per environment.
ENV BACKEND_URL=http://backend:8080 \
    NGINX_ENVSUBST_FILTER="^(BACKEND_URL|NGINX_PORT)$" \
    NGINX_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1:${NGINX_PORT}/healthz >/dev/null || exit 1
