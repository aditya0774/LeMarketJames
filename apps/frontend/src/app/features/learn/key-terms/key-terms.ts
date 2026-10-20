import { Component, computed, input, signal } from '@angular/core';
import { GlossaryTerm } from '../learn-content';

/** Gives each box its own panel id, so aria-controls stays valid with several boxes on a page. */
let nextBoxId = 0;

/**
 * The "Key terms" box at the end of a lesson: one chip per term the lesson teaches. Clicking a chip
 * shows its definition in the box; clicking it again, another chip, or pressing Escape closes it.
 * It owns only which term is open and draws nothing for a lesson with no terms.
 */
@Component({
  selector: 'app-key-terms',
  templateUrl: './key-terms.html',
  styleUrl: './key-terms.css',
  host: { '(keydown.escape)': 'close()' },
})
export class KeyTerms {
  readonly terms = input.required<GlossaryTerm[]>();

  protected readonly panelId = `key-terms-panel-${nextBoxId++}`;

  private readonly open = signal<string | null>(null);

  protected readonly openTerm = computed(() => this.terms().find((t) => t.term === this.open()) ?? null);

  protected isOpen(term: GlossaryTerm): boolean {
    return this.open() === term.term;
  }

  /** Opens the term, or closes it when it is already open. Opening one closes any other. */
  protected toggle(term: GlossaryTerm): void {
    this.open.update((current) => (current === term.term ? null : term.term));
  }

  protected close(): void {
    this.open.set(null);
  }
}
