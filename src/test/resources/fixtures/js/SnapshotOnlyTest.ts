import { render, screen } from '@testing-library/react';
import { format, Card } from '../src/card';

describe('card', () => {
  it('renders', () => {
    const { container } = render(Card({ title: 'a' }));
    expect(container).toMatchSnapshot(); // Noncompliant {{only checks a snapshot ("toMatchSnapshot")}}
  });

  test('formats', () => expect(format(1)).toMatchSnapshot()); // Noncompliant

  it('several snapshots', () => {
    expect(format(1)).toMatchSnapshot('one'); // Noncompliant
    expect(format(2)).toMatchSnapshot('two');
  });

  it('counted snapshot', async () => {
    expect.assertions(1);
    await expect(format(3)).toMatchFileSnapshot('./card.txt'); // Noncompliant
  });

  it.each([1, 2])('formats %i', (n) => {
    expect(format(n)).toMatchSnapshot(); // Noncompliant
  });

  // compliant

  it('snapshot and value', () => {
    expect(format(1)).toMatchSnapshot();
    expect(format(1)).toContain('1');
  });

  it('inline snapshot', () => {
    expect(format(1)).toMatchInlineSnapshot(`"1"`);
  });

  it('error snapshot', () => {
    expect(() => format(-1)).toThrowErrorMatchingSnapshot();
  });

  it('query that throws', () => {
    render(Card({ title: 'a' }));
    expect(screen.getByRole('heading')).toMatchSnapshot();
  });

  it('helper assertion', () => {
    expect(format(1)).toMatchSnapshot();
    expectFormatted(format(1));
  });

  it('local helper', () => {
    expect(format(2)).toMatchSnapshot();
    hasTitle(format(2));
  });

  it.skip('skipped', () => {
    expect(format(1)).toMatchSnapshot();
  });

  it('assert call', () => {
    expect(format(1)).toMatchSnapshot();
    assert(format(1).length > 0);
  });
});

function hasTitle(s: string) {
  expect(s).toContain('title');
}

function expectFormatted(s: string) {
  return s;
}
