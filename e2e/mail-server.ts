import { SMTPServer } from 'smtp-server';
import { simpleParser } from 'mailparser';
import * as http from 'node:http';

/**
 * Captures everything the backend sends (it points at localhost:2525 in the e2e profile) and serves
 * it to tests over HTTP on :2526 — GET /messages (newest last), DELETE /messages.
 */
export interface Captured { to: string[]; from: string; subject: string; text: string; html: string; at: number }

export function startMailCapture(): Promise<() => Promise<void>> {
  const messages: Captured[] = [];
  const smtp = new SMTPServer({
    authOptional: true, disabledCommands: ['STARTTLS'],
    onData(stream, _session, callback) {
      simpleParser(stream).then((m) => {
        const to = Array.isArray(m.to) ? m.to : m.to ? [m.to] : [];
        messages.push({
          to: to.flatMap((a) => a.value.map((v) => v.address ?? '')),
          from: m.from?.value[0]?.address ?? '', subject: m.subject ?? '', text: m.text ?? '',
          html: typeof m.html === 'string' ? m.html : '', at: Date.now(),
        });
        callback();
      }).catch((e) => callback(e));
    },
  });
  const api = http.createServer((req, res) => {
    if (req.method === 'DELETE') { messages.length = 0; res.writeHead(204); return res.end(); }
    res.writeHead(200, { 'content-type': 'application/json' });
    res.end(JSON.stringify(messages));
  });
  return new Promise((resolve) => {
    smtp.listen(2525, '127.0.0.1', () => api.listen(2526, '127.0.0.1', () =>
      resolve(() => new Promise<void>((done) => smtp.close(() => api.close(() => done()))))));
  });
}
