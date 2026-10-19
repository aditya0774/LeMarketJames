import { Component } from '@angular/core';
import { VolumeInsightTile } from './volume-insight-tile/volume-insight-tile';
import { ActiveClientTrendTile } from './active-client-trend-tile/active-client-trend-tile';

/**
 * The analyst's start page, the Volume tab: trading volume against the period before (LMKT-46),
 * then the active-client trend (LMKT-48). The heading and the tabs to every report come from the
 * analyst layout around it.
 */
@Component({
  selector: 'staff-analyst-dashboard',
  imports: [VolumeInsightTile, ActiveClientTrendTile],
  template: `
    <staff-volume-insight-tile />
    <staff-active-client-trend-tile />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 18px;
    }
  `,
})
export class AnalystDashboard {}
