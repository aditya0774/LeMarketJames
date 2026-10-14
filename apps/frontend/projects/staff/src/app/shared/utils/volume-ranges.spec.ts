import { mondayOf, toIsoDay, volumePresets } from './volume-ranges';

describe('volume ranges', () => {
  it('formats the local calendar day', () => {
    expect(toIsoDay(new Date(2026, 0, 5, 23, 59))).toBe('2026-01-05');
  });

  it('finds the Monday of a week, with Sunday belonging to the week before', () => {
    expect(toIsoDay(mondayOf(new Date(2026, 9, 14)))).toBe('2026-10-12'); // Wednesday
    expect(toIsoDay(mondayOf(new Date(2026, 9, 12)))).toBe('2026-10-12'); // Monday
    expect(toIsoDay(mondayOf(new Date(2026, 9, 18)))).toBe('2026-10-12'); // Sunday
  });

  it('builds today, this week so far and last week, each against the period before', () => {
    // Wednesday 14 October 2026.
    const [today, thisWeek, lastWeek] = volumePresets(new Date(2026, 9, 14, 15, 30));

    expect(today.request).toEqual({
      currentFrom: '2026-10-14', currentTo: '2026-10-14',
      previousFrom: '2026-10-13', previousTo: '2026-10-13',
    });
    // Monday to Wednesday against the same three days of last week.
    expect(thisWeek.request).toEqual({
      currentFrom: '2026-10-12', currentTo: '2026-10-14',
      previousFrom: '2026-10-05', previousTo: '2026-10-07',
    });
    expect(lastWeek.request).toEqual({
      currentFrom: '2026-10-05', currentTo: '2026-10-11',
      previousFrom: '2026-09-28', previousTo: '2026-10-04',
    });
  });

  it('crosses a month boundary', () => {
    const [today, thisWeek] = volumePresets(new Date(2026, 10, 1)); // Sunday 1 November

    expect(today.request.previousFrom).toBe('2026-10-31');
    expect(thisWeek.request.currentFrom).toBe('2026-10-26');
    expect(thisWeek.request.previousTo).toBe('2026-10-25');
  });
});
