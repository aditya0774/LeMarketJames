import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Auth, Role } from '../../core/auth/auth';
import { EmployeeInformation } from './employee-information';

/** A signed-in staff session, as login or /me leave it in Auth (C6). */
interface Session { username: string; roles: Role[]; email?: string; fullName?: string }

const OPS: Session = { username: 'seed_ops', roles: ['TRADING_OPS'], email: 'ops@seed.lemarket.com', fullName: 'Olivia Ops' };
const ANALYST: Session = {
  username: 'seed_analyst', roles: ['ANALYST'], email: 'analyst@seed.lemarket.com', fullName: 'Andy Analyst',
};

describe('EmployeeInformation', () => {
  let fixture: ComponentFixture<EmployeeInformation>;

  const el = () => fixture.nativeElement as HTMLElement;
  const field = (name: string) => el().querySelector(`[data-field="${name}"]`)?.textContent?.trim() ?? null;

  function render(session: Session) {
    TestBed.configureTestingModule({
      imports: [EmployeeInformation],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const auth = TestBed.inject(Auth);
    auth.currentUser.set(session.username);
    auth.roles.set(session.roles);
    auth.email.set(session.email ?? null);
    auth.fullName.set(session.fullName ?? null);
    fixture = TestBed.createComponent(EmployeeInformation);
    fixture.detectChanges();
  }

  it('shows a Trading Ops user their own name, username, email and role', () => {
    render(OPS);

    expect(el().querySelector('h1')?.textContent).toContain('Employee information');
    expect(field('fullName')).toBe('Olivia Ops');
    expect(field('username')).toBe('seed_ops');
    expect(field('email')).toBe('ops@seed.lemarket.com');
    expect(field('role')).toBe('Trading Operations');
  });

  it('shows an Analyst their own name, username, email and role', () => {
    render(ANALYST);

    expect(field('fullName')).toBe('Andy Analyst');
    expect(field('username')).toBe('seed_analyst');
    expect(field('email')).toBe('analyst@seed.lemarket.com');
    expect(field('role')).toBe('Analyst');
  });

  it('labels each detail', () => {
    render(ANALYST);

    const labels = Array.from(el().querySelectorAll('dt')).map((dt) => dt.textContent?.trim());
    expect(labels).toEqual(['Name', 'Username', 'Email', 'Role']);
  });

  it('leaves out a detail the session does not carry, rather than showing it empty', () => {
    render({ username: 'seed_ops', roles: ['TRADING_OPS'] });

    expect(field('fullName')).toBeNull();
    expect(field('email')).toBeNull();
    expect(field('username')).toBe('seed_ops');
    expect(field('role')).toBe('Trading Operations');
  });

  it('is read-only: no form, input or button to change anything', () => {
    render(OPS);

    expect(el().querySelectorAll('form, input, select, textarea, button')).toHaveLength(0);
  });
});
