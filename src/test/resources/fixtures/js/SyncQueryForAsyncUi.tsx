import { render, screen, within, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

describe('<SaveButton />', () => {
  let user;
  beforeEach(() => {
    user = userEvent.setup();
  });

  it('shows the saved message', () => {
    render(<SaveButton />);
    user.click(screen.getByRole('button'));
    expect(screen.getByText('Saved')).toBeInTheDocument(); // Noncompliant {{"getByText" runs before the unawaited "user.click" has changed the screen; await the action or use "findByText".}}
  });

  it('hides the form', async () => {
    const u = userEvent.setup();
    render(<Form />);
    u.type(screen.getByLabelText('Name'), 'Ann');
    expect(screen.queryByRole('alert')).toBeNull(); // Noncompliant
  });

  it('keeps the element', () => {
    render(<List />);
    userEvent.setup().click(screen.getByText('Add'));
    const row = within(screen.getByRole('list')).getAllByRole('listitem'); // Noncompliant
    expect(row).toHaveLength(2);
  });

  it('selects an option', () => {
    render(<Picker />);
    user.selectOptions(screen.getByRole('combobox'), 'b');
    screen.getByDisplayValue('b'); // Noncompliant
  });

  it('awaits the action', async () => {
    render(<SaveButton />);
    await user.click(screen.getByRole('button'));
    expect(screen.getByText('Saved')).toBeInTheDocument();
  });

  it('uses findBy after the action', async () => {
    render(<SaveButton />);
    user.click(screen.getByRole('button'));
    expect(await screen.findByText('Saved')).toBeInTheDocument();
  });

  it('uses fireEvent, which is synchronous', () => {
    render(<SaveButton />);
    fireEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Saved')).toBeInTheDocument();
  });

  it('uses the v13 direct API, which is synchronous', () => {
    render(<SaveButton />);
    userEvent.click(screen.getByRole('button'));
    expect(screen.getByText('Saved')).toBeInTheDocument();
  });

  it('keeps the promise and awaits it later', async () => {
    render(<SaveButton />);
    const clicked = user.click(screen.getByRole('button'));
    expect(screen.getByRole('button')).toBeEnabled();
    await clicked;
  });

  it('queries something else between', async () => {
    render(<SaveButton />);
    user.click(screen.getByRole('button'));
    await waitFor(() => expect(screen.getByText('Saved')).toBeInTheDocument());
  });

  it('returns the action', () => {
    render(<SaveButton />);
    return user.click(screen.getByRole('button')).then(() => {
      expect(screen.getByText('Saved')).toBeInTheDocument();
    });
  });

  it('calls a method of another object', () => {
    const page = new PageObject();
    page.click('save');
    expect(page.getByText('Saved')).toBeTruthy();
  });
});
