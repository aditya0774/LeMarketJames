import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { StaffShell } from './staff-shell';

describe('StaffShell', () => {
  it('links to both staff sections and hosts the page', async () => {
    await TestBed.configureTestingModule({
      imports: [StaffShell],
      providers: [provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(StaffShell);
    await fixture.whenStable();

    const page = fixture.nativeElement as HTMLElement;
    const links = Array.from(page.querySelectorAll('nav a')).map((a) => a.getAttribute('href'));
    expect(links).toEqual(['/trading-ops', '/analyst']);
    expect(page.querySelector('router-outlet')).toBeTruthy();
  });
});
