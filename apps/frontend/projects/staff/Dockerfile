# Build context is the Angular workspace root (apps/frontend), not this folder: the staff app
# shares the trading app's package.json and lock file.
FROM node:22-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
RUN npm install --legacy-peer-deps
COPY . .
RUN npx ng build staff --configuration production

FROM nginx:1.27-alpine
LABEL maintainer="LeMarketJames team"

COPY --from=build /app/dist/staff/browser /usr/share/nginx/html
COPY projects/staff/nginx.conf /etc/nginx/conf.d/default.conf

# A self-signed certificate for localhost (BR-02, contract C5), made at build time so a fresh
# checkout needs no extra step. It is for running locally only; a real deployment mounts its own.
RUN apk add --no-cache openssl \
    && mkdir -p /etc/nginx/certs \
    && openssl req -x509 -nodes -newkey rsa:2048 -days 825 -subj "/CN=localhost" \
       -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
       -keyout /etc/nginx/certs/key.pem -out /etc/nginx/certs/cert.pem

EXPOSE 443
