import { price } from './prices';

export function total(items: number[]): number {
  return items.reduce((a, b) => a + price(b), 0);
}

export const count = (items: number[]) => items.length;

export class Basket {
  items: number[] = [];
  add(x: number): void {
    this.items.push(x);
  }
}
