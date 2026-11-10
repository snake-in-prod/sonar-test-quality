import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

const Wrapper = <T,>({ children }: { children: T }) => <div className="wrap">{children}</div>;

describe('<Greeting />', () => {
  it("renders the user's name", async () => {
    render(
      <Wrapper>
        <Greeting name={`Ann ${'B'}`} onSave={() => save({ id: 1 })} />
        <p>Don't panic, it's fine // not a comment</p>
        <>{items.map(i => <li key={i}>{i > 1 ? 'big' : "small"}</li>)}</>
      </Wrapper>
    );
    await userEvent.click(screen.getByRole('button'));
    expect(await screen.findByText(/saved/i)).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Ann')).toBeVisible());
  });

  test('generic helper', () => {
    const pick = <K extends string>(k: K) => k;
    expect(pick('a')).toEqual('a');
  });
});
