# Multi-stage build example
FROM node:18-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --omit=dev

# Copy application source
COPY . .

# Build application (if needed)
# RUN npm run build

# Production stage
FROM node:18-alpine

WORKDIR /app

# Copy dependencies and built artifacts from builder stage
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app .

# Create non-root user
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nodejs -u 1001 && \
    chown -R nodejs:nodejs /app

USER nodejs

EXPOSE 8080

CMD ["node", "index.js"]
