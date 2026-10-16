import { Component } from '@angular/core';
import { VolumeInsightTile } from './volume-insight-tile/volume-insight-tile';

/**
 * The analyst's start page, the Volume tab: trading volume against the period before (LMKT-46).
 * The heading and the tabs to every report come from the analyst layout around it.
 */
@Component({
  selector: 'staff-analyst-dashboard',
  imports: [VolumeInsightTile],
  template: `<staff-volume-insight-tile />`,
})
export class AnalystDashboard {}
