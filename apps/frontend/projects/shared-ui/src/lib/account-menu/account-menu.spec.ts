import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AccountMenu } from './account-menu';
import { Theme } from '../theme/theme';

describe('AccountMenu', () => {
  let fixture: ComponentFixture<AccountMenu>;
  const el = () => fixture.nativeElement as HTMLElement;
  const avatar = () => el().querySelector<HTMLButtonElement>('.avatar-btn')!;
  const buttonNamed = (name: string) =>
    Array.from(el().querySelectorAll<HTMLButtonElement>('.menu button')).find((b) => b.textContent?.trim() === name)!;

  beforeEach(() => {
    localStorage.clear();
    // The optional page link is a router link; its page exists so following it navigates cleanly.
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: 'employee-information', children: [] }])] });
    fixture = TestBed.createComponent(AccountMenu);
    fixture.componentRef.setInput('name', 'jane@example.com');
    fixture.componentRef.setInput('detail', 'Investing account');
    fixture.detectChanges();
  });

  it('shows the initial and starts closed', () => {
    expect(avatar().textContent?.trim()).toBe('J');
    expect(avatar().getAttribute('aria-expanded')).toBe('false');
    expect(el().querySelector('.menu')).toBeNull();
  });

  it('opens to show who is signed in', () => {
    avatar().click();
    fixture.detectChanges();

    expect(avatar().getAttribute('aria-expanded')).toBe('true');
    expect(el().querySelector('.menu-who')?.textContent).toContain('jane@example.com');
    expect(el().querySelector('.menu-who')?.textContent).toContain('Investing account');
  });

  it('switches the theme', () => {
    avatar().click();
    fixture.detectChanges();

    buttonNamed('Light').click();
    fixture.detectChanges();

    expect(TestBed.inject(Theme).current()).toBe('light');
    expect(buttonNamed('Light').getAttribute('aria-pressed')).toBe('true');
    expect(buttonNamed('Dark').getAttribute('aria-pressed')).toBe('false');
  });

  it('reports a log out', () => {
    let loggedOut = 0;
    fixture.componentInstance.loggedOut.subscribe(() => loggedOut++);
    avatar().click();
    fixture.detectChanges();

    buttonNamed('Log out').click();

    expect(loggedOut).toBe(1);
  });

  it('closes on Escape and returns focus to the avatar', () => {
    avatar().click();
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(el().querySelector('.menu')).toBeNull();
    expect(document.activeElement).toBe(avatar());
  });

  it('shows no page link unless one is given, as in the trading app', () => {
    avatar().click();
    fixture.detectChanges();

    expect(el().querySelector('.menu a')).toBeNull();
  });

  it('links to the page about the account and closes when it is followed', async () => {
    fixture.componentRef.setInput('pageLink', '/employee-information');
    fixture.componentRef.setInput('pageLabel', 'Employee information');
    avatar().click();
    fixture.detectChanges();

    const link = el().querySelector<HTMLAnchorElement>('.menu a.page-link')!;
    expect(link.textContent?.trim()).toBe('Employee information');
    expect(link.getAttribute('href')).toBe('/employee-information');

    link.click();
    // Let the navigation finish before the test ends.
    await fixture.whenStable();
    fixture.detectChanges();
    expect(el().querySelector('.menu')).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/employee-information');
  });

  it('closes on a click elsewhere but not inside', () => {
    avatar().click();
    fixture.detectChanges();

    el().querySelector<HTMLElement>('.menu-who')!.click();
    fixture.detectChanges();
    expect(el().querySelector('.menu')).not.toBeNull();

    document.body.click();
    fixture.detectChanges();
    expect(el().querySelector('.menu')).toBeNull();
  });
});
