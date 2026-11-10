import { Button } from './Button';

export default class Panel {
  state = { open: false };

  toggle = () => {
    this.state.open = !this.state.open;
  };

  render() {
    return <div><Button onClick={this.toggle} /></div>;
  }

  static create(): Panel {
    return new Panel();
  }
}
