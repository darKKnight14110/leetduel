FROM node:22-alpine AS dependencies
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS builder
WORKDIR /app
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_AUTH_API_URL=/api
ARG NEXT_PUBLIC_GATEWAY_API_URL=/api
ARG NEXT_PUBLIC_WS_GATEWAY_URL=
ENV NEXT_PUBLIC_AUTH_API_URL=$NEXT_PUBLIC_AUTH_API_URL
ENV NEXT_PUBLIC_GATEWAY_API_URL=$NEXT_PUBLIC_GATEWAY_API_URL
ENV NEXT_PUBLIC_WS_GATEWAY_URL=$NEXT_PUBLIC_WS_GATEWAY_URL
RUN npm run build

FROM node:22-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S nextjs -g 1001 && adduser -S nextjs -u 1001 -G nextjs
COPY --from=builder /app/public ./public
COPY --from=builder --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nextjs /app/.next/static ./.next/static
USER nextjs
EXPOSE 3000
CMD ["node", "server.js"]
