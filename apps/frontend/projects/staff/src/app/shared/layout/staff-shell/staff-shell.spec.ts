import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { StaffShell } from './staff-shell';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth } from '../../../core/auth/auth';

describe('StaffShell', () => {
  it('links Operations to their dashboard and trade search and hosts the page', async () => {
    await TestBed.configureTestingModule({
      imports: [StaffShell],
      providers: [provideRouter([]), provideHttpClient()],
    }).compileComponents();
    TestBed.inject(Auth).roles.set(['TRADING_OPS']);
    const fixture = TestBed.createComponent(StaffShell);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    const links = Array.from(page.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/trading-ops', '/trade-search']);
    expect(page.querySelector('router-outlet')).toBeTruthy();
  });

  // Every role sees its own section and nothing of another's; COMPLIANCE has no section yet.
  for (const [role, expected] of [['ANALYST', ['/analyst']], ['COMPLIANCE', []], ['CLIENT', []]] as const) {
    it('links ' + role + ' to its own pages only', async () => {
      await TestBed.configureTestingModule({
        imports: [StaffShell],
        providers: [provideRouter([]), provideHttpClient()],
      }).compileComponents();
      TestBed.inject(Auth).roles.set([role]);
      const fixture = TestBed.createComponent(StaffShell);
      await fixture.whenStable();

      const links = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('nav a'));
      expect(links.map((a) => a.getAttribute('href'))).toEqual([...expected]);
    });
  }

  it('signs out through the gateway and returns to the staff login', async () => {
    await TestBed.configureTestingModule({
      imports: [StaffShell],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    const auth = TestBed.inject(Auth);
    auth.currentUser.set('seed_analyst');
    auth.roles.set(['ANALYST']);
    const fixture = TestBed.createComponent(StaffShell);
    await fixture.whenStable();
    // Resolves when the shell navigates, which it does only once the gateway has answered.
    const navigatedTo = new Promise<unknown>((resolve) => {
      TestBed.inject(Router).navigate = async (commands) => {
        resolve(commands);
        return true;
      };
    });

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.logout-btn')!.click();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/logout').flush({ message: 'Logged out' });

    expect(await navigatedTo).toEqual(['/login']);
    expect(auth.currentUser()).toBeNull();
  });
});
