import { provideRouter, Router } from '@angular/router';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { AppShell } from './app-shell';
import { Auth } from '../../../core/auth/auth';
import { expect, vi } from 'vitest';

describe('AppShell', () => {
	let fixture: ComponentFixture<AppShell>;
	let auth: { currentUser: ReturnType<typeof signal<string | null>>; logout: ReturnType<typeof vi.fn> };
	let router: Router;

	beforeEach(async () => {
		auth = {
			currentUser: signal('jane@example.com'),
			logout: vi.fn().mockResolvedValue(undefined),
		};

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

	it('names the signed-in user in the account menu', () => {
		expect(fixture.nativeElement.querySelector('.avatar-btn').textContent.trim()).toBe('J');
	});

	it('puts the Learn tab right after Orders', () => {
		const tabs = Array.from<HTMLAnchorElement>(fixture.nativeElement.querySelectorAll('.app-tabs a'));
		const names = tabs.map((tab) => tab.textContent?.trim());
		expect(names[names.indexOf('Orders') + 1]).toBe('Learn');
		expect(tabs[names.indexOf('Learn')].getAttribute('href')).toBe('/learn');
	});

	it('logs out and replaces the protected history entry', async () => {
		const avatar = fixture.nativeElement.querySelector('.avatar-btn') as HTMLButtonElement;
		avatar.click();
		fixture.detectChanges();
		const button = fixture.nativeElement.querySelector('button[title="Log out"], .logout-btn') as HTMLButtonElement;

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
