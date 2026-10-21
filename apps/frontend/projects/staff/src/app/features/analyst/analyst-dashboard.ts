import { Component } from '@angular/core';
import { VolumeInsightTile } from './volume-insight-tile/volume-insight-tile';
import { ActiveClientTrendTile } from './active-client-trend-tile/active-client-trend-tile';
import { MostActiveStocksTile } from './most-active-stocks-tile/most-active-stocks-tile';
import { InsightFlags } from './insight-flags/insight-flags';
import { WhatChanged } from './insight-flags/what-changed';

/**
 * The analyst's start page, the Volume tab: what changed most (BR-17), then trading volume against
 * the period before (LMKT-46), the active-client trend (LMKT-48) and the most active stocks. The
 * heading and the tabs to every report come from the analyst layout around it.
 */
@Component({
  selector: 'staff-analyst-dashboard',
  imports: [WhatChanged, VolumeInsightTile, ActiveClientTrendTile, MostActiveStocksTile],
  // One store per dashboard: the tiles report their flags to it and the strip reads them.
  providers: [InsightFlags],
  template: `
    <staff-what-changed />
    <staff-volume-insight-tile />
    <staff-active-client-trend-tile />
    <staff-most-active-stocks-tile />
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
