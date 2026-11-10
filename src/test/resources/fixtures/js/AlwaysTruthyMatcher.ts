import { render, screen } from '@testing-library/react';
import { load, List } from '../src/list';

describe('list', () => {
  it('literals', () => {
    expect([]).toBeTruthy(); // Noncompliant {{"[]" is always truthy, so ".toBeTruthy()" cannot fail}}
    expect({ a: 1 }).toBeDefined(); // Noncompliant
    expect(true).toBe(true); // Noncompliant {{"true" is compared with itself}}
    expect('ok').not.toBeFalsy(); // Noncompliant {{".not.toBeFalsy()" cannot fail on the literal "'ok'"}}
    expect(null).toBeDefined(); // Noncompliant
    expect(0).toBeFalsy(); // Noncompliant
  });

  it('objects and functions', () => {
    expect(new List()).not.toBeNull(); // Noncompliant
    expect(() => load()).toBeDefined(); // Noncompliant
    expect(/a+/).toBeTruthy(); // Noncompliant
  });

  it('unawaited query', async () => {
    render(load());
    expect(screen.findByText('Saved')).toBeTruthy(); // Noncompliant {{is a promise}}
    expect(screen.queryAllByRole('row')).toBeTruthy(); // Noncompliant
    expect(screen.queryByText('Error')).toBeDefined(); // Noncompliant {{returns null, not undefined}}
    expect(document.querySelector('.error')).not.toBeUndefined(); // Noncompliant
  });

  it('arrays from calls', () => {
    const items = load();
    expect(items.filter(i => i.done)).toBeTruthy(); // Noncompliant
    expect(Object.keys(items)).toBeDefined(); // Noncompliant
  });

  it('self comparison', () => {
    const result = load();
    expect(result.items).toEqual(result.items); // Noncompliant
  });

  // compliant

  it('real values', async () => {
    const items = load();
    expect(items).toBeTruthy();
    expect(items.length).toBeTruthy();
    expect(items.find(i => i.done)).toBeDefined();
    expect(await screen.findByText('Saved')).toBeTruthy();
    expect(screen.queryByText('Error')).not.toBeNull();
    expect(screen.queryByText('Error')).toBeNull();
    expect(screen.queryAllByRole('row')).toHaveLength(2);
  });

  it('literal matched against something else', () => {
    expect(true).toBe(load().ok);
    expect(load().ok).toBe(true);
    expect([]).toEqual(load());
    expect('').toBeTruthy();
    expect(0).toBeTruthy();
    expect(1).toBe(2);
  });

  it('comparisons that can fail', () => {
    expect(load()).toEqual(load());
    expect({}).toBe({});
    expect(new List()).toEqual(new List());
  });

  it('async values', async () => {
    await expect(Promise.resolve([])).resolves.toBeTruthy();
    expect(load).toBeDefined();
  });

  it('chai and other libraries', () => {
    expect([]).to.be.ok;
    expect([], 'to be truthy');
  });

  it('templates with values', () => {
    const name = load().name;
    expect(`${name}`).toBeTruthy();
  });
});
