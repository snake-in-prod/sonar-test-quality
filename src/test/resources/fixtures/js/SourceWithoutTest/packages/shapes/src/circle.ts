// reached through the package entry src/index.ts, which only re-exports
export function area(r: number): number {
  return Math.PI * r * r;
}

export function perimeter(r: number): number {
  return 2 * Math.PI * r;
}

export function diameter(r: number): number {
  return 2 * r;
}
