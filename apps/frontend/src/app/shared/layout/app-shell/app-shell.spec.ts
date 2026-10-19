import { provideRouter, Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShell } from './app-shell';
import { Auth } from '../../../core/auth/auth';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  let auth: { logout: ReturnType<typeof jasmine.createSpy> };
  let router: Router;

  beforeEach(async () => {
    auth = { logout: jasmine.createSpy('logout').and.resolveTo() };

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: Auth, useValue: auth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShell);
    router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    fixture.detectChanges();
  });

  it('logs out and replaces the protected history entry', async () => {
    const button = fixture.nativeElement.querySelector('button[title="Log out"]') as HTMLButtonElement;

    button.click();
    await fixture.whenStable();

    expect(auth.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/'], { replaceUrl: true });
  });

  it('navigates home even when logout fails', async () => {
    auth.logout.and.rejectWith(new Error('logout failed'));
    const button = fixture.nativeElement.querySelector('button[title="Log out"]') as HTMLButtonElement;

    button.click();
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/'], { replaceUrl: true });
  });
});