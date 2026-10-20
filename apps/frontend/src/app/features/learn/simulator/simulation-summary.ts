import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { Simulation } from './simulator-math';

/**
 * The result of a simulation in a sentence a beginner can read: what was put in, what it bought,
 * what it is worth now, and the gain or loss. Presentational: the page runs the simulation and
 * passes it in.
 *
 * A gain or loss is shown with the up/down colours, an arrow and a word, so it never depends on
 * colour alone.
 */
@Component({
  selector: 'app-simulation-summary',
  imports: [CurrencyPipe, DatePipe, DecimalPipe],
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
