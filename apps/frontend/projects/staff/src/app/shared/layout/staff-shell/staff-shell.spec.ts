import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { StaffShell } from './staff-shell';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth } from '../../../core/auth/auth';

describe('StaffShell', () => {
  it('renders the logout button and hosts the page', async () => {
    await TestBed.configureTestingModule({
      imports: [StaffShell],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
    TestBed.inject(Auth).roles.set(['TRADING_OPS']);
    const fixture = TestBed.createComponent(StaffShell);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.staff-role')?.textContent?.trim()).toBe('Trading Operations');
    // Log out sits in the account menu behind the avatar.
    page.querySelector<HTMLButtonElement>('.avatar-btn')!.click();
    fixture.detectChanges();
    expect(page.querySelector('.logout-btn')).toBeTruthy();
    expect(page.querySelector('router-outlet')).toBeTruthy();
    // The role is a label, never a link to another role's workspace.
    expect(page.querySelectorAll('header a:not(.brand)')).toHaveLength(0);
  });

  it('signs out through the API and returns to the staff login', async () => {
    await TestBed.configureTestingModule({
      imports: [StaffShell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const auth = TestBed.inject(Auth);
    auth.currentUser.set('seed_analyst');
    auth.roles.set(['ANALYST']);
    const fixture = TestBed.createComponent(StaffShell);
    await fixture.whenStable();
    // Resolves when the shell navigates, which it does only once the API has answered.
    const navigation = new Promise<{ commands: unknown; extras: unknown }>((resolve) => {
      TestBed.inject(Router).navigate = async (commands, extras) => {
        resolve({ commands, extras });
        return true;
      };
    });

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.avatar-btn')!.click();
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.logout-btn')!.click();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/logout').flush({ message: 'Logged out' });

    expect(await navigation).toEqual({ commands: ['/login'], extras: { replaceUrl: true } });
    expect(auth.currentUser()).toBeNull();
  });
});
