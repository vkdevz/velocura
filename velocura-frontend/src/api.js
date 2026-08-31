import axios from 'axios';

export const getBaseUrl = () => {
  // 1. Explicit Vite env variable (set at build time or via environment)
  if (import.meta.env.VITE_API_BASE_URL) {
    return import.meta.env.VITE_API_BASE_URL.replace(/\/+$/, '');
  }
  // 2. Global window override (useful for runtime configuration)
  if (typeof window !== 'undefined' && window.__VELOCURA_API_BASE_URL__) {
    return window.__VELOCURA_API_BASE_URL__.replace(/\/+$/, '');
  }
  // 3. LocalStorage override for runtime custom configuration
  if (typeof window !== 'undefined') {
    try {
      const customUrl = localStorage.getItem('velocura_api_url');
      if (customUrl) return customUrl.replace(/\/+$/, '');
    } catch {
      // ignore
    }
  }
  // 4. Deployment environment heuristics
  if (typeof window !== 'undefined') {
    const host = window.location.hostname;
    // Local development
    if (host === 'localhost' || host === '127.0.0.1' || host === '0.0.0.0') {
      return 'http://localhost:8080';
    }
    // Render deployment fallback
    if (host.endsWith('.onrender.com') && !host.includes('velocura-backend')) {
      return 'https://velocura-backend.onrender.com';
    }
    // Vercel / Netlify deployment fallback
    if (host.endsWith('.vercel.app') || host.endsWith('.netlify.app')) {
      return 'https://velocura-backend.onrender.com';
    }
    // Unified domain / reverse proxy fallback
    return window.location.origin;
  }
  return 'http://localhost:8080';
};

const api = axios.create({
  baseURL: getBaseUrl(),
  timeout: 30000,
});

// Request interceptor to automatically attach JWT token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token') || localStorage.getItem('velocura_jwt');
    if (token && token !== 'undefined' && token !== 'null') {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor to catch HTML SPA fallbacks, token expirations, and rate limit errors
api.interceptors.response.use(
  (response) => {
    // Detect SPA fallback returning index.html for API routes
    if (
      typeof response.data === 'string' &&
      (response.data.trim().startsWith('<!doctype html') ||
        response.data.trim().startsWith('<html') ||
        response.data.includes('<div id="root">'))
    ) {
      const error = new Error('Received HTML page instead of API JSON response. Backend API is unavailable or URL is misconfigured.');
      error.response = {
        status: 502,
        data: { message: 'Backend service unreachable. Please ensure the backend server is running and online.' }
      };
      return Promise.reject(error);
    }
    return response;
  },
  (error) => {
    if (error.response && error.response.status === 401) {
      // If unauthorized on protected API call
      const path = window.location.pathname;
      if (path.includes('/dashboard')) {
        localStorage.removeItem('token');
        localStorage.removeItem('velocura_jwt');
        localStorage.removeItem('role');
        localStorage.removeItem('email');
        localStorage.removeItem('firstName');
        localStorage.removeItem('lastName');
        window.location.href = '/login?expired=true';
      }
    } else if (error.response && error.response.status === 429) {
      const retryAfter = error.response.headers?.['retry-after'] || error.response.data?.retryAfterSeconds || 60;
      console.warn(`[Security Rate Limiter] Request throttled. Please retry after ${retryAfter}s.`);
    }
    return Promise.reject(error);
  }
);

export default api;

