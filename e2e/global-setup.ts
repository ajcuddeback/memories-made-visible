import { startMailCapture } from './mail-server';

/** Starts the SMTP capture before the suite; Playwright calls the returned teardown afterwards. */
export default async function globalSetup() {
  const stop = await startMailCapture();
  return async () => { await stop(); };
}
