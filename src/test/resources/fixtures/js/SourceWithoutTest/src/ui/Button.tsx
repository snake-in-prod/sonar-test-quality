// reached through ui/index.tsx
export function Button(props: { onClick: () => void }) {
  return <button onClick={props.onClick}>ok</button>;
}

export const Icon = () => <i />;

export function Label() {
  return <span />;
}
