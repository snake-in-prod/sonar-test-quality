// the entry named by the package's "exports" string
export function upper(s: string): string {
  return s.toUpperCase();
}

export function lower(s: string): string {
  return s.toLowerCase();
}

export function title(s: string): string {
  return upper(s.slice(0, 1)) + s.slice(1);
}
