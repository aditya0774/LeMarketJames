const fs = require('fs');
const path = require('path');

// Parse .env file manually
function loadEnv(envPath) {
  const env = {};
  if (fs.existsSync(envPath)) {
    const content = fs.readFileSync(envPath, 'utf-8');
    content.split('\n').forEach(line => {
      const trimmed = line.trim();
      if (trimmed && !trimmed.startsWith('#')) {
        const [key, ...valueParts] = trimmed.split('=');
        if (key) {
          env[key.trim()] = valueParts.join('=').trim();
        }
      }
    });
  }
  return env;
}

const envPath = path.join(__dirname, '../.env');
const env = loadEnv(envPath);

const apiBaseUrl = env.API_BASE_URL || 'http://localhost:8081';
const envContent = `// Auto-generated from .env file. Do not edit manually.
// Run \`npm run load-env\` to regenerate this file.

export const environment = {
  apiBaseUrl: \`${apiBaseUrl}\`,
};\n`;

const envFilePath = path.join(__dirname, '../apps/frontend/src/environments/environment.ts');
fs.writeFileSync(envFilePath, envContent, 'utf-8');

console.log(`✓ Environment configured: API_BASE_URL=${apiBaseUrl}`);
