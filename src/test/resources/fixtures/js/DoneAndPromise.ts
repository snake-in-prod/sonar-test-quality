import { load } from '../src/store';

describe('store', () => {
  it('async arrow with done', async done => { // Noncompliant {{This async test also takes "done"}}
    expect(await load(1)).toBe(1);
    done();
  });

  it('async function with done', async function (done) { // Noncompliant
    expect(await load(1)).toBe(1);
    done();
  });

  it('returns a then chain', (done) => { // Noncompliant {{returns a promise}}
    return load(1).then(r => {
      expect(r).toBe(1);
      done();
    });
  });

  it('expression body is a promise', cb => load(1).then(() => cb())); // Noncompliant

  it('returns a new promise', (finish: () => void) => { // Noncompliant
    return new Promise<void>(resolve => {
      resolve();
      finish();
    });
  });

  it('returns an async assertion', function (done) { // Noncompliant
    return expect(load(1)).resolves.toBe(1);
  });

  it('other name, called', async (end) => { // Noncompliant
    await load(1);
    end();
  });

  // compliant

  it('done only', (done) => {
    load(1).then(r => {
      expect(r).toBe(1);
      done();
    });
  });

  it('async only', async () => {
    expect(await load(1)).toBe(1);
  });

  it('promise only', () => {
    return load(1).then(r => expect(r).toBe(1));
  });

  it('done with a return inside a nested callback', (done) => {
    load(1).then(r => {
      return r.then(x => x);
    }).then(() => done());
  });

  it.each([1, 2])('table row %i', async (id) => {
    expect(await load(id)).toBeDefined();
  });

  it('test context, not a callback', async (ctx) => {
    ctx.skip();
  });

  it('plain return', (done) => {
    if (!load) {
      return done();
    }
    done();
  });

  it('destructured fixtures', async ({ page }) => {
    await page.goto('/');
  });
});
