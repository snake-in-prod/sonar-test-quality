import { render, screen, renderHook } from '@testing-library/react';
import { vi } from 'vitest';

describe('<Form />', () => {
  it('renders the fields', () => {
    const onSave = jest.fn(); // Noncompliant {{"onSave" is passed in but never asserted on}}
    render(<Form onSave={onSave} />);
    expect(screen.getByLabelText('Name')).toBeInTheDocument();
  });

  it('subscribes', () => {
    const listener = vi.fn(); // Noncompliant
    const bus = new Bus();
    bus.on('change', listener);
    bus.emit('change');
    expect(bus.size).toBe(1);
  });

  it('builds a hook', () => {
    const onSubmit: jest.Mock = jest.fn(); // Noncompliant
    const { result } = renderHook(() => useForm({ onSubmit, options: { onError: onSubmit } }));
    expect(result.current.valid).toBe(true);
  });

  it('passes it in nested markup', () => {
    let onClose = vi.fn(); // Noncompliant
    render(
      <Dialog title="x">
        <Button kind="close" onClick={onClose} />
      </Dialog>
    );
    expect(screen.getByRole('dialog')).toBeVisible();
  });

  it('asserts the call', () => {
    const onSave = jest.fn();
    render(<Form onSave={onSave} />);
    expect(onSave).toHaveBeenCalledWith({ name: 'Ann' });
  });

  it('asserts through the mock property', () => {
    const onSave = jest.fn();
    render(<Form onSave={onSave} />);
    expect(onSave.mock.calls).toHaveLength(1);
  });

  it('is a stub with a value', () => {
    const load = jest.fn().mockResolvedValue({ id: 1 });
    render(<Profile load={load} />);
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('is a stub with an implementation', () => {
    const format = vi.fn((x) => x.toUpperCase());
    expect(render(<Label format={format} />)).toBeTruthy();
  });

  it('is configured later', () => {
    const fetchUser = jest.fn();
    fetchUser.mockReturnValue(user);
    render(<Profile fetchUser={fetchUser} />);
    expect(screen.getByText('Ann')).toBeInTheDocument();
  });

  it('is checked by a helper', () => {
    const onSave = jest.fn();
    render(<Form onSave={onSave} />);
    expectCalledOnce(onSave);
  });

  it('is checked through an object', () => {
    const onSave = jest.fn();
    const props = { onSave };
    render(<Form {...props} />);
    expect(props.onSave).toHaveBeenCalled();
  });

  it('is called directly', () => {
    const cb = jest.fn();
    run(() => cb());
    expect(done).toBe(true);
  });

  it('passes an inline mock', () => {
    render(<Form onSave={jest.fn()} />);
    expect(screen.getByRole('form')).toBeInTheDocument();
  });

  it('is never passed', () => {
    const unused = jest.fn();
    expect(1).toBe(1);
  });
});
