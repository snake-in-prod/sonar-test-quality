// two imports away from a test: not reached
export function deep(): number { // Noncompliant
  return 1;
}

export function deeper(): number {
  return deep() + 1;
}

export const deepest = async (): Promise<number> => deeper();
