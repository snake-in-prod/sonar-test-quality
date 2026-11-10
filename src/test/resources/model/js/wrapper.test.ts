function itWithUser(name: string, fn: (user: User) => Promise<void>) {
  it(name, async () => fn(await login()));
}

const itAsAdmin = (name, fn) => test(name, async () => {
  await fn(admin());
});

const itPassesThrough = (name, fn) => it(name, fn);

function check(name, expected) {
  it(name, () => {
    expect(compute()).toBe(expected);
  });
}

describe('users', () => {
  itWithUser('sees the profile', async (user) => {
    expect(user.profile).toBeDefined();
    expect(user.name).toBe('a');
  });

  itAsAdmin('deletes', async (admin) => {
    const spy = jest.fn();
    await admin.delete(spy);
    expect(spy).toHaveBeenCalled();
  });

  itPassesThrough('plain', () => {
    expect(1).toBe(1);
  });

  check('parameterized', 2);

  itWithUser(name, async (user) => {});
});
