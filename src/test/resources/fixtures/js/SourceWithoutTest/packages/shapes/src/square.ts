// reached by a deep import of the package
export function side(a: number): number {
  return Math.sqrt(a);
}

export function surface(s: number): number {
  return s * s;
}

export function edge(s: number): number {
  return 4 * s;
}
