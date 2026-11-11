export function itWithUser(name: string, fn: (user: User) => Promise<void>) {
  it(name, async () => fn(await login()));
}

export default function itAsAdmin(name: string, fn: () => void) {
  test(name, fn);
}
