import { InsightFlag } from '../../../shared/models/insight-flag.model';

/**
 * A flag as the server would send it with the default 25 % setting (C5), for specs: flagged when
 * the move is 25 % or more either way, or when there was nothing before. Test-only; no app code
 * imports it.
 */
export function flagFor(changePercent: number | null, direction?: InsightFlag['direction']): InsightFlag {
  const way = direction ?? directionOf(changePercent);
  const flagged = changePercent === null ? way !== 'FLAT' : Math.abs(changePercent) >= 25;
  return { flagged, changePercent, direction: way, thresholdPercent: 25 };
}

/** Nothing before counts as a rise, as the server reports it. */
function directionOf(changePercent: number | null): InsightFlag['direction'] {
  if (changePercent === null || changePercent > 0) {
    return 'UP';
  }
  return changePercent < 0 ? 'DOWN' : 'FLAT';
}
