export function fmt(x: number): string {
  return pad(String(x));
}

export function pad(s: string): string {
  return s.padStart(3);
}

export function trim(s: string): string {
  return s.trim();
}
