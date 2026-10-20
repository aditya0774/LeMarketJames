import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { AppShell } from './app-shell';
import { Auth } from '../../../core/auth/auth';

describe('AppShell', () => {
  let logoutCalls: number;

  beforeEach(() => {
    logoutCalls = 0;
    TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [provideHttpClient(), provideRouter([])],
    });
    const auth = TestBed.inject(Auth);
    auth.currentUser.set('jane@example.com');
    auth.logout = async () => {
      logoutCalls++;
    };
  });

  it('names the signed-in user in the account menu', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.avatar-btn').textContent.trim()).toBe('J');
  });

  it('puts the Learn tab right after Orders', () => {
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();

    const tabs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('.app-tabs a'));
    const names = tabs.map((a) => a.textContent?.trim());
    expect(names[names.indexOf('Orders') + 1]).toBe('Learn');
    expect(tabs[names.indexOf('Learn')].getAttribute('href')).toBe('/learn');
  });

  it('logs out and goes to the landing page', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(AppShell);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.avatar-btn').click();
    fixture.detectChanges();
    const logOut = Array.from<HTMLButtonElement>(fixture.nativeElement.querySelectorAll('.menu button')).find(
      (b) => b.textContent?.trim() === 'Log out',
    )!;
    logOut.click();
    await fixture.whenStable();

    expect(logoutCalls).toBe(1);
    expect(navigate).toHaveBeenCalledWith(['/']);
  });
});
