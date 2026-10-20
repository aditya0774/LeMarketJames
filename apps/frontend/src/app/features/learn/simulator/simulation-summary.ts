import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { SignedCurrencyPipe } from '../../../shared/pipes/signed-currency.pipe';
import { Simulation } from './simulator-math';

/**
 * The result of a simulation as three stat cards: what was put in (and what it bought), what it is
 * worth now, and the gain or loss. Presentational: the page runs the simulation and
 * passes it in.
 *
 * A gain or loss is shown with the up/down colours, an arrow, a sign and a word, so it never depends on
 * colour alone.
 */
@Component({
  selector: 'app-simulation-summary',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, SignedCurrencyPipe],
  templateUrl: './simulation-summary.html',
  styleUrl: './simulation-summary.css',
})
export class SimulationSummary {
  readonly simulation = input.required<Simulation>();
  readonly stockName = input.required<string>();
  /** The dollars put in, as the user entered them. */
  readonly amount = input.required<number>();

  protected readonly isGain = computed(() => this.simulation().gain >= 0);
  protected readonly word = computed(() => (this.isGain() ? 'gain' : 'loss'));
  protected readonly arrow = computed(() => (this.isGain() ? '▲' : '▼'));
  protected readonly gainAbs = computed(() => Math.abs(this.simulation().gain));
  protected readonly percentAbs = computed(() => Math.abs(this.simulation().gainPercent));
  protected readonly sign = computed(() => (this.isGain() ? '+' : '−'));
}
