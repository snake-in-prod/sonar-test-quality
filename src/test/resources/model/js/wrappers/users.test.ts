import itAsAdmin, { itWithUser } from './helpers/users';
import { withUser } from './helpers';
import * as helpers from './helpers/users';

describe('users', () => {
  itWithUser('sees the profile', async (user) => {
    expect(user.profile).toBeDefined();
  });

  withUser('renames', async (user) => {
    expect(user.name).toBe('b');
    expect(user.saved).toBe(true);
  });

  itAsAdmin('deletes', () => {
    expect(remove()).toBe(true);
  });

  helpers.itWithUser('through a namespace', async (user) => {
    expect(user).toBeDefined();
  });
});
