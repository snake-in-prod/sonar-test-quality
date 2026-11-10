import { fetchUser, parse } from '../src/user';

describe('user', () => {
  it('bare expect', () => {
    const user = parse('{"name":"a"}');
    expect(user.name); // Noncompliant {{"expect(user.name)" is never finished with a matcher}}
  });

  it('expect without semicolon', async () => {
    const user = await fetchUser(1)
    expect(user) // Noncompliant
    const other = await fetchUser(2)
    expect(other).toBeDefined()
  });

  it('resolves without matcher', async () => {
    await expect(fetchUser(1)).resolves; // Noncompliant {{"expect(fetchUser(1)).resolves" is never finished}}
  });

  it('not without matcher', () => {
    expect(parse('x')).not; // Noncompliant
  });

  it('matcher not called', () => {
    expect(parse('{}')).toBeTruthy; // Noncompliant {{"toBeTruthy" is not called}}
  });

  it('soft expect without matcher', () => {
    expect.soft(parse('{}')); // Noncompliant
  });

  it('arrow body', () => expect(parse('{}'))); // Noncompliant

  // compliant

  it('finished with a matcher', () => {
    expect(parse('{}')).toEqual({});
    expect(parse('{}')).not.toBeNull();
  });

  it('matcher on the next line', async () => {
    await expect(fetchUser(1))
      .resolves
      .toEqual({ name: 'a' });
  });

  it('chai style', () => {
    expect(parse('{}')).to.be.ok;
    expect(parse('{}')).to.deep.equal({});
  });

  it('expect kept for later', () => {
    const e = expect(parse('{}'));
    e.toEqual({});
    const later =
      expect(parse('{}'));
    later.toBeDefined();
  });

  it('assertion arguments of another library', () => {
    expect(parse('{}'), 'to equal', {});
  });

  it('asymmetric matchers', () => {
    expect(parse('{}')).toEqual(expect.objectContaining({}));
    expect(parse('{}')).toEqual(expect.anything());
  });

  it('passed to a helper', () => {
    check(expect(parse('{}')));
    return expect(parse('{}'));
  });

  it('around an assertion helper', () => {
    expect(assertParsed(parse('{}')));
  });

  it('expect in a condition', () => {
    if (expect(parse('{}'))) {
      expect(1).toBe(1);
    }
  });
});

function assertParsed(v: unknown) {
  expect(v).toBeDefined();
}

function check(e: unknown) {
  return e;
}

// outside a test
expect(parse('{}'));
