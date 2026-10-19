import { provideRouter, Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppShell } from './app-shell';
import { Auth } from '../../../core/auth/auth';
import { expect, vi } from 'vitest';

describe('AppShell', () => {
  let fixture: ComponentFixture<AppShell>;
  let auth: { logout: ReturnType<typeof vi.fn> };
  let router: Router;

  beforeEach(async () => {
    auth = { logout: vi.fn().mockResolvedValue(undefined) };

    await TestBed.configureTestingModule({
      imports: [AppShell],
      providers: [
        provideRouter([]),
        { provide: Auth, useValue: auth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppShell);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
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
    auth.logout.mockRejectedValue(new Error('logout failed'));

    await expect(fixture.componentInstance['logout']()).rejects.toThrow('logout failed');

    expect(router.navigate).toHaveBeenCalledWith(['/'], { replaceUrl: true });
  });
});