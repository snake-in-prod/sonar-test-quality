import { EventEmitter } from 'events';
import { load, save } from '../src/store';

describe('store', () => {
  it('then without return', () => {
    load(1).then(r => expect(r).toBe(1)); // Noncompliant {{"then" callback is not waited for}}
  });

  it('catch in a chain', () => {
    save({ id: 1 })
      .then(() => load(1))
      .catch(e => { // Noncompliant
        expect(e.message).toMatch('denied');
      });
  });

  it('timer', () => {
    const value = compute();
    setTimeout(() => { // Noncompliant {{"setTimeout" callback runs after the test has ended}}
      expect(value).toBe(2);
    }, 100);
  });

  it('then in an if block of an async test', async () => {
    const ready = true;
    if (ready) {
      load(2).then(function (r) { // Noncompliant
        assert.equal(r, 2);
      });
    }
  });

  it('event handler registered last', () => {
    const emitter = new EventEmitter();
    start(emitter);
    emitter.on('data', d => expect(d).toEqual({ ok: true })); // Noncompliant
  });

  // compliant

  it('returned', () => {
    return load(1).then(r => expect(r).toBe(1));
  });

  it('awaited', async () => {
    await load(1).then(r => expect(r).toBe(1));
  });

  it('done callback', done => {
    load(1).then(r => {
      expect(r).toBe(1);
      done();
    });
  });

  it('wrapped in an awaited promise', async () => {
    await new Promise<void>(resolve => {
      setTimeout(() => {
        expect(compute()).toBe(2);
        resolve();
      }, 10);
    });
  });

  it('awaited later', async () => {
    const p = load(1);
    p.then(r => expect(r).toBe(1));
    await p;
  });

  it('then without assertion', () => {
    load(1).then(r => console.log(r));
  });

  it('event emitted synchronously after the handler', () => {
    const emitter = new EventEmitter();
    emitter.on('data', d => expect(d).toBe(1));
    emitter.emit('data', 1);
  });

  it('guarded by expect.assertions', () => {
    expect.assertions(1);
    load(1).then(r => expect(r).toBe(1));
  });

  it('then result kept', () => {
    const value = load(1).then(r => expect(r).toBe(1));
    track(value);
  });
});
