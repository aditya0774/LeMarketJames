import { Component, computed, input } from '@angular/core';

/** One character of the shown text: a digit rolls, anything else ($ , . + − %) is drawn as is. */
export interface RollingCell {
  /** Place counted from the right, so a digit keeps its column when the number grows. */
  place: number;
  char: string;
  digit: number | null;
}

/**
 * Splits already-formatted text into cells keyed by place from the right: when "$999.99" becomes
 * "$1,000.00" the cents and units columns are the same elements and roll, and only the new
 * leading columns appear. Exported for unit testing.
 */
export function rollingCells(text: string): RollingCell[] {
  const chars = [...text];
  return chars.map((char, i) => ({
    place: chars.length - i,
    char,
    digit: char >= '0' && char <= '9' ? Number(char) : null,
  }));
}

/**
 * Shows formatted text (a money amount, a percentage) whose digits roll up or down to their new
 * value, like an odometer, so a live figure visibly moves instead of jumping.
 *
 * Formatting stays with the caller (the currency and number pipes), so this only animates. The
 * digit strips are CSS-generated content, which keeps the element's text, and what a screen reader
 * hears, the plain value; the motion is turned off for users who ask for reduced motion (base.css).
 */
@Component({
  selector: 'app-rolling-number',
  template: `
    <span class="sr-only">{{ text() }}</span>
    <span class="rn" aria-hidden="true">
      @for (cell of cells(); track cell.place) {
        @if (cell.digit !== null) {
          <span class="rn-digit" [style.--rn-digit]="cell.digit"></span>
        } @else {
          <span class="rn-char" [attr.data-char]="cell.char"></span>
        }
      }
    </span>
  `,
  styleUrl: './rolling-number.css',
})
export class RollingNumber {
  readonly text = input.required<string>();

  protected readonly cells = computed(() => rollingCells(this.text()));
}
