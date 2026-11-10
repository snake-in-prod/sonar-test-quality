type Handler = (x: number) => void;

export function start(h: Handler) { // Noncompliant {{add tests for its 3 functions}}
  [1, 2].forEach((x) => h(x));
}

export class Job {
  run(): void {
    start(() => undefined);
  }

  stop(): void {}
}
