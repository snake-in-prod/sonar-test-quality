// imported through a tsconfig alias, which is not resolved
export const alias = 'a';

export function one() { // Noncompliant
  return 1;
}

export function two() {
  return 2;
}

export function three() {
  return 3;
}
