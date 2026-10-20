import { Component, ElementRef, HostListener, inject, input, output, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Theme, ThemeName } from '../theme/theme';

/**
 * The avatar button at the right of each app's signed-in header and the panel it opens: who is
 * signed in (and, in the staff app, their role and a link to their Employee information), the
 * Dark/Light theme switch and "Log out". Shared
 * by the trading and staff apps so the header looks and behaves the same in both.
 *
 * It is a disclosure (a button with aria-expanded that shows a panel of ordinary buttons) rather
 * than an ARIA menu, so every control works with Tab and Enter and keeps its plain button role.
 * Escape or a click anywhere else closes it; Escape also puts focus back on the avatar.
 */
@Component({
  selector: 'lm-account-menu',
  imports: [RouterLink],
  template: `
    <div class="menu-wrap">
      <button
        #trigger
        type="button"
        class="avatar-btn"
        aria-label="Account menu"
        aria-controls="account-menu"
        [attr.aria-expanded]="open()"
        (click)="toggle()"
      >
        {{ initial() }}
      </button>
      @if (open()) {
        <div class="menu" id="account-menu">
          <div class="menu-who">
            <b>{{ name() }}</b>
            <span>{{ detail() }}</span>
          </div>
          @if (pageLink(); as link) {
            <a class="menu-item page-link" [routerLink]="link" (click)="open.set(false)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <rect x="3" y="5" width="18" height="14" rx="2" />
                <circle cx="9" cy="11" r="2" />
                <path d="M6 16c.6-1.2 1.7-2 3-2s2.4.8 3 2M14 10h4M14 14h3" />
              </svg>
              {{ pageLabel() }}
            </a>
          }
          <fieldset class="menu-theme">
            <legend>Appearance</legend>
            <div class="seg">
              @for (option of themes; track option.value) {
                <button type="button" [attr.aria-pressed]="theme.current() === option.value" (click)="theme.set(option.value)">
                  <span class="theme-dots" aria-hidden="true"><i [style.background]="option.dots[0]"></i><i [style.background]="option.dots[1]"></i></span>
                  {{ option.label }}
                </button>
              }
            </div>
          </fieldset>
          <button type="button" class="menu-item logout-btn" (click)="loggedOut.emit()">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M15 4h4v16h-4M10 17l5-5-5-5M15 12H3" />
            </svg>
            Log out
          </button>
        </div>
      }
    </div>
  `,
})
export class AccountMenu {
  protected readonly theme = inject(Theme);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');

  /** Who is signed in, e.g. their login email. */
  readonly name = input.required<string>();
  /** The line under the name, e.g. the role. */
  readonly detail = input('');
  /**
   * An optional page about the signed-in account, linked under the name (the staff app's Employee
   * information, LMKT-144). Left unset, the menu shows no link, as in the trading app.
   */
  readonly pageLink = input<string | null>(null);
  /** The text of that link. */
  readonly pageLabel = input('');
  readonly loggedOut = output<void>();

  protected readonly open = signal(false);

  /** Each theme is previewed by its two team colours, so these are fixed rather than tokens. */
  protected readonly themes: readonly { value: ThemeName; label: string; dots: [string, string] }[] = [
    { value: 'dark', label: 'Dark', dots: ['#552583', '#FDB927'] },
    { value: 'light', label: 'Light', dots: ['#006BB6', '#ED174C'] },
  ];

  protected initial(): string {
    return this.name().trim().charAt(0).toUpperCase() || '?';
  }

  protected toggle(): void {
    this.open.update((value) => !value);
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.open()) {
      this.open.set(false);
      this.trigger()?.nativeElement.focus();
    }
  }
}
