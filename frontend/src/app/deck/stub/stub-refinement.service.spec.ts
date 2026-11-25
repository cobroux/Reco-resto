import { firstValueFrom } from 'rxjs';
import { STUB_DECK_INPUT } from './stub-restaurants';
import { StubRefinementService } from './stub-refinement.service';

describe('StubRefinementService', () => {
  const candidates = STUB_DECK_INPUT.restaurants;
  const ids = (list: readonly { id: string }[]) => list.map((r) => r.id);

  it('returns the candidates not excluded, in their initial order, truncated to the slots', async () => {
    const service = new StubRefinementService();
    const excluded = [candidates[0].id, candidates[2].id];

    const result = await firstValueFrom(
      service.refine({ candidates, reactions: [], excludedIds: excluded, slots: 3 }),
    );

    expect(ids(result)).toEqual([candidates[1].id, candidates[3].id, candidates[4].id]);
  });

  it('errors when its failure mode is on', async () => {
    const service = new StubRefinementService();
    service.failing = true;

    await expect(
      firstValueFrom(service.refine({ candidates, reactions: [], excludedIds: [], slots: 3 })),
    ).rejects.toThrow();
  });
});
