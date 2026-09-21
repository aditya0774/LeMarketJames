import { isOpenStatus, matchesFilter, statusLabel, statusPillClass } from './order-status';

describe('order-status', () => {
  it('treats in-flight statuses as open', () => {
    expect(['SUBMITTED', 'ACCEPTED', 'PENDING', 'DELAYED'].every((s) => isOpenStatus(s as never))).toBe(true);
    expect(isOpenStatus('FILLED')).toBe(false);
    expect(isOpenStatus('REJECTED')).toBe(false);
  });

  it('maps filter chips onto backend statuses', () => {
    expect(matchesFilter('REJECTED', 'ALL')).toBe(true);
    expect(matchesFilter('DELAYED', 'OPEN')).toBe(true);
    expect(matchesFilter('FILLED', 'OPEN')).toBe(false);
    expect(matchesFilter('FILLED', 'FILLED')).toBe(true);
    expect(matchesFilter('PENDING', 'REJECTED')).toBe(false);
  });

  it('picks pill colours and labels', () => {
    expect(statusPillClass('FILLED')).toBe('pill-filled');
    expect(statusPillClass('REJECTED')).toBe('pill-rejected');
    expect(statusPillClass('SUBMITTED')).toBe('pill-pending');
    expect(statusLabel('SUBMITTED')).toBe('Submitted');
  });
});
