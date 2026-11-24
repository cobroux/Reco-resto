import { firstValueFrom } from 'rxjs';
import { StubDeckSource } from './stub-deck-source';
import { STUB_DECK_INPUT } from './stub-restaurants';

describe('StubDeckSource', () => {
  it('emits the stub deck input', async () => {
    await expect(firstValueFrom(new StubDeckSource().load())).resolves.toBe(STUB_DECK_INPUT);
  });
});
