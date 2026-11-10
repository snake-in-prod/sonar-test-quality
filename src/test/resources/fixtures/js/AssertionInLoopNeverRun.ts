import { render, screen } from '@testing-library/react';
import { listUsers, validate, report } from '../src/users';

const CASES = [
  { input: 'a', valid: true },
  { input: '', valid: false },
];
const BY_NAME = { ann: 1, bob: 2 };

function connectionsOf<Keys extends (string | undefined)[]>(...keys: Keys) {
  return keys.map((k) => registry.get(k));
}

describe('users', () => {
  it('for of over a computed list', async () => {
    const users = await listUsers();
    for (const u of users) expect(u.active).toBe(true); // Noncompliant {{loop over "users"}}
  });

  it('forEach over a call', () => {
    validate({}).forEach(e => { // Noncompliant
      expect(e.code).toBe('required');
    });
  });

  it('for of over a member of a computed value', async () => {
    const result = await report();
    for (const row of result.rows) { // Noncompliant
      expect(row.total).toBeGreaterThan(0);
    }
  });

  it('for of over keys of a computed object', () => {
    const totals = report();
    for (const k of Object.keys(totals)) { // Noncompliant
      assert.ok(totals[k] >= 0);
    }
  });

  it('queryAll can be empty', () => {
    render(List());
    screen.queryAllByRole('listitem').forEach(item => expect(item).toHaveClass('row')); // Noncompliant
  });

  // compliant

  it('length asserted too', async () => {
    const users = await listUsers();
    expect(users).toHaveLength(2);
    for (const u of users) expect(u.active).toBe(true);
  });

  it('literal table', () => {
    for (const c of CASES) {
      expect(validate(c.input).length === 0).toBe(c.valid);
    }
  });

  it('entries of a literal object', () => {
    for (const [name, id] of Object.entries(BY_NAME)) {
      expect(name.length).toBeGreaterThan(id);
    }
  });

  it('inline array', () => {
    [1, 2, 3].forEach(n => expect(n).toBeGreaterThan(0));
    for (const n of [4, 5]) expect(n).toBeGreaterThan(3);
  });

  it('getAll throws when empty', () => {
    render(List());
    screen.getAllByRole('listitem').forEach(item => expect(item).toHaveClass('row'));
  });

  it('guarded by expect.assertions', async () => {
    expect.assertions(2);
    for (const u of await listUsers()) expect(u.active).toBe(true);
  });

  it('classic for loop', () => {
    const users = listUsers();
    for (let i = 0; i < users.length; i++) expect(users[i]).toBeDefined();
  });

  it('factory with a count', () => {
    for (const u of makeUsers(3)) expect(u.id).toBeDefined();
  });

  it('variable set outside the test', () => {
    for (const u of shared) expect(u.id).toBeDefined();
  });

  it('one result per argument', () => {
    const connections = connectionsOf('a', 'b');
    connections.forEach((conn) => expect(conn.send).not.toBeCalled());
    for (const conn of connectionsOf('c')) expect(conn).toBeDefined();
  });

  it('map inside an expect', async () => {
    const users = await listUsers();
    expect(users.map(u => u.id)).toEqual([1, 2]);
  });
});
