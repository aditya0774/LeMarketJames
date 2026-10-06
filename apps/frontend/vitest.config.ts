import { defineConfig } from 'vitest/config';
import { getVitestConfig } from '@angular/build';

export default defineConfig(
  getVitestConfig({
    polyfills: ['zone.js', 'zone.js/testing'],
  })
);
