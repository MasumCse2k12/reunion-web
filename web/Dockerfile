# syntax=docker/dockerfile:1

#   docker build -t sammalani/alumni-web ./web
#
# Vercel and Netlify build this app from source themselves; this image is for
# running it anywhere else — a VPS, a laptop, a CI smoke test.

# ---- build ------------------------------------------------------------------

FROM node:22-alpine AS build

WORKDIR /app

# Lockfile first, so `npm ci` is reused whenever only source files changed.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci

COPY . .

# Typecheck here rather than trusting it happened upstream: `vite build` strips
# types without checking them, so a broken build would otherwise ship happily.
RUN npm run typecheck && npm run build


# ---- serve -------------------------------------------------------------------

FROM nginx:1.31-alpine

# nginx official image auto-processes *.conf.template files in /etc/nginx/templates/
# with envsubst before starting, so ${WEB_PORT} is substituted at runtime.
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Default port; override with WEB_PORT env var at runtime.
ENV WEB_PORT=80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${WEB_PORT}/healthz" >/dev/null 2>&1 || exit 1
