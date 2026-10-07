import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StaffShell } from './staff-shell';
import { provideHttpClient } from '@angular/common/http';
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
});
