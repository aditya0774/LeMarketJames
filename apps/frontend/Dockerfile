FROM node:22-alpine AS build

WORKDIR /app
COPY package.json package-lock.json ./
# The cache mount keeps npm downloads between builds (BuildKit).
RUN --mount=type=cache,target=/root/.npm npm install --legacy-peer-deps
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine

COPY --from=build /app/dist/lemarket-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
