import { vi } from 'vitest';
import { STUB_DECK_INPUT } from '../deck/stub/stub-restaurants';
import { StubSearchService } from './stub-search.service';

describe('StubSearchService', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('emits the stub deck input once and completes after 500 ms', () => {
    vi.useFakeTimers();
    const next = vi.fn();
    const complete = vi.fn();

    new StubSearchService().search('asiatique près d’Opéra').subscribe({ next, complete });

    vi.advanceTimersByTime(499);
    expect(next).not.toHaveBeenCalled();
    expect(complete).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(next).toHaveBeenCalledTimes(1);
    expect(next).toHaveBeenCalledWith(STUB_DECK_INPUT);
    expect(complete).toHaveBeenCalledTimes(1);
  });
});
