import { defineConfig, devices } from '@playwright/test';

/**
 * The suite drives the real stack: Spring Boot (e2e profile, scratch Postgres `mmv_e2e`, email to the
 * capture server on :2525) and `ng serve` with its /api proxy. Tests share one database and run in
 * file order, one worker, so later files can rely on what earlier ones set up.
 */
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env['CI'] ? [['github'], ['html', { open: 'never' }]] : [['list'], ['html', { open: 'never' }]],
  globalSetup: './global-setup',
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    {
      command: 'bash scripts/run-backend.sh',
      url: 'http://localhost:8080/readyz',
      timeout: 240_000,
      reuseExistingServer: false,
      stdout: 'ignore',
      stderr: 'pipe',
    },
    {
      command: 'npm start -- --port 4200',
      cwd: '../frontend',
      url: 'http://localhost:4200/',
      timeout: 240_000,
      reuseExistingServer: !process.env['CI'],
      stdout: 'ignore',
      stderr: 'pipe',
    },
  ],
});
