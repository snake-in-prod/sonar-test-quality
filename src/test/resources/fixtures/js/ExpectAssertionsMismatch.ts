import { fetchUser, save } from '../src/api';
import { expectUser, sameUser } from './helpers/user';

describe('api', () => {
  it('one expect, two counted', async () => {
    expect.assertions(2); // Noncompliant {{"expect.assertions(2)" does not match the 1 assertion this test makes; set it to 1.}}
    await expect(fetchUser(-1)).rejects.toThrow('not found');
  });

  it('three expects, two counted', async () => {
    expect.assertions(2); // Noncompliant {{the 3 assertions}}
    const user = await fetchUser(1);
    expect(user.id).toBe(1);
    expect(user.name).toBe('a');
    expect(user.roles).toEqual([]);
  });

  it('then callback, too many counted', () => {
    expect.assertions(2); // Noncompliant
    return fetchUser(1).then(user => {
      expect(user.id).toBe(1);
    });
  });

  test('none at all', async () => {
    expect.assertions(1); // Noncompliant {{the 0 assertions}}
    await save({ id: 1 });
  });

  // compliant

  it('matching count', async () => {
    expect.assertions(2);
    const user = await fetchUser(1);
    expect(user.id).toBe(1);
    await expect(save(user)).resolves.toBeUndefined();
  });

  it('try and catch', async () => {
    expect.assertions(1);
    try {
      await fetchUser(-1);
      expect(true).toBe(false);
    } catch (e) {
      expect(e).toMatchObject({ code: 404 });
    }
  });

  it('expects in a loop', async () => {
    expect.assertions(3);
    for (const id of [1, 2, 3]) {
      expect(await fetchUser(id)).toBeDefined();
    }
  });

  it('expects in a callback run several times', async () => {
    expect.assertions(2);
    const onSave = jest.fn((u) => expect(u).toBeDefined());
    await save({ id: 1 }, onSave);
    await save({ id: 2 }, onSave);
  });

  it('forEach', () => {
    expect.assertions(2);
    [1, 2].forEach(n => expect(n).toBeGreaterThan(0));
  });

  it('imported helper', async () => {
    expect.assertions(2);
    expectUser(await fetchUser(1));
    expect(sameUser(1, 1)).toBe(true);
  });

  it('local helper', async () => {
    expect.assertions(3);
    checkIds(await fetchUser(1));
    expect(1).toBe(1);
  });

  it('conditional expect', async () => {
    expect.assertions(1);
    const user = await fetchUser(1);
    if (user.admin) {
      expect(user.roles).toContain('admin');
    } else {
      expect(user.roles).toEqual([]);
    }
  });

  it('count from a variable', async () => {
    const n = 2;
    expect.assertions(n);
    expect(await fetchUser(1)).toBeDefined();
  });

  it('asymmetric matchers are not assertions', async () => {
    expect.assertions(1);
    expect(await fetchUser(1)).toEqual(expect.objectContaining({ id: expect.any(Number) }));
  });

  it('catch callback', () => {
    expect.assertions(1);
    return fetchUser(-1).then(() => expect(true).toBe(false)).catch(e => expect(e).toBeDefined());
  });
});

function checkIds(u: { id: number }) {
  expect(u.id).toBeGreaterThan(0);
  expect(typeof u.id).toBe('number');
}
