import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { Register } from './register';

describe('Register', () => {
  let component: Register;
  let fixture: ComponentFixture<Register>;
  let httpMock: HttpTestingController;

  const validFormData = {
    firstName: 'Jane',
    middleName: '',
    lastName: 'Doe',
    streetAddress: '123 Main St',
    apartment: '',
    city: 'Springfield',
    state: 'Illinois',
    zipCode: '62701',
    ssn: '123-45-6789',
    initialDeposit: '5000',
    investmentExperience: 'beginner' as const,
    employmentStatus: 'employed' as const,
    dateOfBirth: '1990-01-01',
    email: 'jane@example.com',
    phoneNumber: '(555) 123-4567',
    password: 'Passw0rd!',
    confirmPassword: 'Passw0rd!',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: 'login', component: Register }]),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('submits a mapped payload to the register endpoint on valid data', async () => {
    component.registerData = { ...validFormData };
    component.onSubmit();

    const req = httpMock.expectOne((request) => request.url.endsWith('/api/auth/register'));
    expect(req.request.body.username).toBe('jane@example.com');
    expect(req.request.body.fullName).toBe('Jane Doe');
    expect(req.request.body.employmentStatus).toBe('EMPLOYED');
    expect(req.request.body.country).toBe('US');
    req.flush({ username: 'jane@example.com', message: 'User registered successfully' });
    await fixture.whenStable();
  });

  it('surfaces a duplicate-email error message from the backend', async () => {
    component.registerData = { ...validFormData };
    component.onSubmit();

    const req = httpMock.expectOne((request) => request.url.endsWith('/api/auth/register'));
    req.flush(
      { message: 'Email is already registered' },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect((component as any).errorMessage()).toBe('Email is already registered');
  });
});
