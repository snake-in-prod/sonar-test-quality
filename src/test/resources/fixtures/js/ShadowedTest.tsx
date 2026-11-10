import { render, screen } from '@testing-library/react';

describe('UserForm', () => {
  it('saves the user', () => {
    expect(save()).toBe(true);
  });

  it('saves the user', () => { // Noncompliant {{Test "saves the user" is already defined at line 4}}
    expect(save()).toBe(true);
  });

  test("saves the user", () => expect(save()).toBe(true)); // Noncompliant

  it.only(`saves the user`, async () => { // Noncompliant
    expect(await saveAsync()).toBe(true);
  });

  it('validates the email', () => {
    expect(validate('a@b.c')).toBe(true);
  });
  it('validates the email', () => expect(validate('x')).toBe(false)); it('rejects empty', () => {}) // Noncompliant

  // same name in another describe block
  describe('when offline', () => {
    it('validates the email', () => {
      expect(validate('a@b.c')).toBe(true);
    });
  });

  // a skipped copy
  it.skip('rejects empty', () => {});

  // table tests produce one name per row
  it.each([1, 2])('handles %i', (n) => {
    expect(n).toBeGreaterThan(0);
  });
  it.each([3, 4])('handles %i', (n) => {
    expect(n).toBeGreaterThan(2);
  });

  // dynamic names
  it(`renders ${'a'}`, () => {
    expect(1).toBe(1);
  });
  it(`renders ${'b'}`, () => {
    expect(1).toBe(1);
  });
  it('renders ' + 'a', () => {});
  it('renders ' + 'b', () => {});

  // the same name in exclusive branches
  if (process.env.CI) {
    it('loads the config', () => expect(load()).toBeTruthy());
  } else {
    it('loads the config', () => expect(load()).toBeFalsy());
  }
  if (isWindows) it('uses backslashes', () => {});
  if (!isWindows) it('uses backslashes', () => {});
  isMac ? it('uses cmd', () => {}) : it('uses cmd', () => {});

  // names that only look alike
  it('Saves the user', () => {
    expect(save()).toBe(true);
  });
  it('saves the user ', () => {
    expect(save()).toBe(true);
  });
});

describe('Other', () => {
  it('saves the user', () => {
    expect(save()).toBe(true);
  });
});

function sharedExamples() {
  it('loads the config', () => {});
}
