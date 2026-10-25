export const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];
export const MON = MONTHS.map((m) => m.slice(0, 3));
export const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

const pad = (n: number) => String(n).padStart(2, '0');

/** Local midnight today. */
export function today(): Date {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), d.getDate());
}

export function toISO(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Parses yyyy-mm-dd as a *local* date (never shifts across midnight). */
export function fromISO(s: string): Date {
  const [y, m, d] = String(s).split('-').map(Number);
  return new Date(y, (m || 1) - 1, d || 1);
}

export function fmtDate(iso: string): string {
  return fromISO(iso).toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
}

export function fmtLongDate(d: Date): string {
  return d.toLocaleDateString('en-US', { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
}

export function money(n: number | string | null | undefined): string {
  return '$' + Math.round(num(n)).toLocaleString('en-US');
}

/** "$1,800" → 1800; "" → 0 */
export function num(v: number | string | null | undefined): number {
  if (typeof v === 'number') return isFinite(v) ? v : 0;
  if (v == null) return 0;
  const n = Number(String(v).replace(/[^0-9.]/g, ''));
  return isFinite(n) ? n : 0;
}

export function sameMonth(iso: string, y: number, m: number): boolean {
  const d = fromISO(iso);
  return d.getFullYear() === y && d.getMonth() === m;
}
