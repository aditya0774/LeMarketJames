import { Component, computed, input } from '@angular/core';

const WIDTH = 300;
const HEIGHT = 56;
const PADDING = 4;

/**
 * Builds an SVG path that stretches the values across the chart, with the lowest value
 * at the bottom and the highest at the top. Exported for unit testing.
 */
export function sparklinePath(values: readonly number[], width = WIDTH, height = HEIGHT): string {
  if (values.length < 2) {
    return '';
  }
  const min = Math.min(...values);
  const max = Math.max(...values);
  // A flat series would divide by zero; draw it across the middle instead.
  const range = max - min || 1;
  const stepX = width / (values.length - 1);
  const usable = height - PADDING * 2;
  return values
    .map((v, i) => {
      const x = i * stepX;
      const y = max === min ? height / 2 : PADDING + usable - ((v - min) / range) * usable;
      return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
}

/**
 * Minimal line chart for a price series, coloured by overall direction. `size="panel"` is
 * the full-width trade chart (mockup `.mini-chart`); `size="row"` fits a table cell.
 */
@Component({
  selector: 'app-sparkline',
  template: `
    <svg [class.mini-chart]="size() === 'panel'" [class.row-chart]="size() === 'row'"
         [class.up]="direction() === 'up'" [class.down]="direction() === 'down'"
         viewBox="0 0 300 56" preserveAspectRatio="none" role="img" [attr.aria-label]="label()">
      <path [attr.d]="path()" />
    </svg>
  `,
})
export class Sparkline {
  readonly values = input<readonly number[]>([]);
  readonly label = input('Price trend');
  readonly size = input<'panel' | 'row'>('panel');

  protected readonly path = computed(() => sparklinePath(this.values()));

  protected readonly direction = computed<'up' | 'down' | 'flat'>(() => {
    const v = this.values();
    if (v.length < 2 || v[v.length - 1] === v[0]) {
      return 'flat';
    }
    return v[v.length - 1] > v[0] ? 'up' : 'down';
  });
}
