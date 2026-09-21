import { Pipe, PipeTransform } from '@angular/core';

const formatter = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 });

/** Renders large figures the way the mockup does: 6.2M volume, 118.4B market cap. */
@Pipe({ name: 'compactNumber' })
export class CompactNumberPipe implements PipeTransform {
  transform(value: number | null | undefined): string {
    return value == null || Number.isNaN(value) ? '—' : formatter.format(value);
  }
}
