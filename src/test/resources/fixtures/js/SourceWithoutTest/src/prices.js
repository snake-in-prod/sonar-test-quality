// reached through cart.ts, which a test imports
const { deep } = require('./deep');

function price(x) {
  return gross(x);
}

const net = function (x) {
  return x;
};

const gross = (x) => net(x) * deep();

module.exports = { price, net, gross };
