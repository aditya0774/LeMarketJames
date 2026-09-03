import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Auth } from '../../../core/auth/auth';
import { Login } from './login';

describe('Login', () => {
  let component: Login;
  let fixture: ComponentFixture<Login>;
  let authMock: { login: (request: { username: string; password: string }) => Promise<{ username: string; message: string }> };
  let loginCalls: Array<{ username: string; password: string }>;
  let router: Router;
  let navigateCalls = 0;
  let navigateArgs: unknown[] = [];

  beforeEach(async () => {
    loginCalls = [];
    authMock = {
      login: async (request) => {
        loginCalls.push(request);
        return { username: request.username, message: 'Login successful' };
      },
    };

    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: Auth, useValue: authMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    navigateCalls = 0;
    navigateArgs = [];
    router.navigate = async (...commands: unknown[]) => {
      navigateCalls += 1;
      navigateArgs = commands;
      return true;
    };
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should not submit when form is invalid', async () => {
    await component.submit();

    expect(loginCalls.length).toBe(0);
    expect((component as any).form.get('username')?.touched).toBeTruthy();
    expect((component as any).form.get('password')?.touched).toBeTruthy();
  });

  it('should authenticate registered user and navigate to home', async () => {
    (component as any).form.setValue({ username: 'alice', password: 'Pass123!' });
    await component.submit();

    expect(loginCalls).toEqual([{ username: 'alice', password: 'Pass123!' }]);
    expect(navigateCalls).toBe(1);
    expect(navigateArgs).toEqual([['/']]);
    expect((component as any).errorMessage()).toBeNull();
  });

  it('should show error message for invalid credentials', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({
        status: 400,
        error: { message: 'Invalid username or password' },
      });
    };

    (component as any).form.setValue({ username: 'alice', password: 'WrongPass!' });
    await component.submit();

    expect((component as any).errorMessage()).toBe('Invalid username or password');
  });

  it('should show lockout delay message after repeated failed attempts', async () => {
    authMock.login = async () => {
      throw new HttpErrorResponse({
        status: 400,
        error: {
          message: 'Account temporarily locked due to too many failed attempts. Try again later.',
        },
      });
    };

    (component as any).form.setValue({ username: 'alice', password: 'Pass123!' });
    await component.submit();

    expect((component as any).errorMessage()).toContain('temporarily locked');
  });
});
