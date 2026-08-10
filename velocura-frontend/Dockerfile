# ==============================================================================
# STAGE 1: BUILD STAGE
# ==============================================================================
FROM node:22-alpine AS builder
WORKDIR /app

# Cache package dependencies
COPY package*.json ./
RUN npm ci

# Copy codebase and compile HTML/CSS/JS chunks
COPY . .
RUN npm run build

# ==============================================================================
# STAGE 2: RUNTIME WEB SERVING
# ==============================================================================
FROM nginx:1.25-alpine

# Set default PORT environment variable for Nginx template substitution
ENV PORT=80

# Copy built static files from builder stage
COPY --from=builder /app/dist /usr/share/nginx/html

# Mount custom Nginx SPA routing configuration template for dynamic PORT substitution
COPY nginx.conf /etc/nginx/templates/default.conf.template

# Expose Nginx server port
EXPOSE 80

# Run Nginx in foreground
CMD ["nginx", "-g", "daemon off;"]
