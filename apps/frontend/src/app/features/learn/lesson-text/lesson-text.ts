import { Component, computed, input, signal } from '@angular/core';
import { GlossaryTerm } from '../learn-content';
import { TextSegment } from '../term-links';

/**
 * One paragraph of a lesson. Words that are glossary terms are buttons; opening one shows its
 * definition right under the paragraph, so the reader never has to leave the text to look it up.
 * It takes ready-made segments and owns only which term is open, so it knows nothing about how
 * terms are found.
 */
@Component({
  selector: 'app-lesson-text',
  templateUrl: './lesson-text.html',
  styleUrl: './lesson-text.css',
  host: { '(keydown.escape)': 'close()' },
})
export class LessonText {
  readonly segments = input.required<TextSegment[]>();

  private readonly open = signal<string | null>(null);

  protected readonly openTerm = computed(() => this.segments().find((s) => s.term?.term === this.open())?.term ?? null);

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
