// Jest style
const { add, div, run, fetchUser } = require('./calc');
jest.mock('./api');

describe('Calculator', () => {
  beforeEach(() => {
    jest.resetAllMocks();
  });

  it('adds numbers', () => {
    expect(add(1, 2)).toBe(3);
    expect(add(2, 2)).not.toBe(5);
  });

  test('uses a spy', () => {
    const spy = jest.spyOn(console, 'log');
    const cb = jest.fn().mockReturnValue(1);
    run(cb);
    expect(cb).toHaveBeenCalledWith(expect.any(Number));
  });

  it.skip('skipped one', () => {
    expect(1).toBe(1);
  });

  xit('also skipped', function () {
    expect(true).toBeTruthy();
  });

  it('calls back', done => {
    setTimeout(() => {
      expect(/a\/b/.test('a/b')).toBe(true);
      done();
    }, 10);
  });

  it('resolves', async () => {
    await expect(fetchUser()).resolves.toEqual({ name: 'a' });
  });

  it.each([[1, 1, 2], [1, 2, 3]])('adds %i + %i', (a, b, sum) => {
    expect(add(a, b)).toBe(sum);
  });

  it.each`
    a    | b    | sum
    ${1} | ${1} | ${2}
  `('template table $a + $b', ({ a, b, sum }) => {
    expect(add(a, b)).toBe(sum);
  });

  it.todo('write this later');

  describe.skip('nested', () => {
    it('inherits skip', () => expect(div(4, 2)).toBe(2));
  });
});

it('top level without assertion', () => {
  const total = 10 / 2 / 5;
  console.log(`total ${total}`);
});
