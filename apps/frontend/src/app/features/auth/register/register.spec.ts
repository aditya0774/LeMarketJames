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

  it('should reject submission when mandatory registration data is missing', () => {
    component.onSubmit();

    expect(component.validationErrors['firstName']).toBeTruthy();
    expect(component.validationErrors['lastName']).toBeTruthy();
    expect(component.validationErrors['username']).toBeTruthy();
    expect(component.validationErrors['streetAddress']).toBeTruthy();
    expect(component.validationErrors['ssn']).toBeTruthy();
    expect(component.validationErrors['initialDeposit']).toBeTruthy();
    expect(component.validationErrors['dateOfBirth']).toBeTruthy();
    expect(component.validationErrors['email']).toBeTruthy();
    expect(component.validationErrors['phoneNumber']).toBeTruthy();
    expect(component.validationErrors['password']).toBeTruthy();
  });

  it('should downgrade experienced onboarding choice when deposit is below threshold', () => {
    component.registerData.initialDeposit = '90000';
    component.registerData.investmentExperience = 'experienced';

    component.onInitialDepositInput();

    expect(component.registerData.investmentExperience).toBe('beginner');
  });

  it('should reject experienced profile when final submitted deposit is below 100000', () => {
    component.registerData = {
      firstName: 'Alice',
      middleName: '',
      lastName: 'Smith',
      username: 'alice123',
      streetAddress: '123 Main St',
      apartment: '',
      city: 'Boston',
      state: 'Massachusetts',
      zipCode: '02110',
      ssn: '123-45-6789',
      initialDeposit: '99999',
      investmentExperience: 'experienced',
      employmentStatus: 'employed',
      dateOfBirth: '1990-01-01',
      email: 'alice@example.com',
      phoneNumber: '(555) 123-4567',
      password: 'Pass123!',
      confirmPassword: 'Pass123!',
    };

    component.onSubmit();

    expect(component.validationErrors['investmentExperience']).toContain('100,000');
    expect(component.registerData.investmentExperience).toBe('beginner');
  });

  it('should accept valid self-service onboarding data without validation errors', async () => {
    component.registerData = {
      firstName: 'Alice',
      middleName: '',
      lastName: 'Smith',
      username: 'alice123',
      streetAddress: '123 Main St',
      apartment: '',
      city: 'Boston',
      state: 'Massachusetts',
      zipCode: '02110',
      ssn: '123-45-6789',
      initialDeposit: '100000',
      investmentExperience: 'experienced',
      employmentStatus: 'employed',
      dateOfBirth: '1990-01-01',
      email: 'alice@example.com',
      phoneNumber: '(555) 123-4567',
      password: 'Pass123!',
      confirmPassword: 'Pass123!',
    };

    component.onSubmit();

    const req = httpMock.expectOne('http://10.14.129.6:8081/api/auth/register');
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, message: 'Registration successful' });

    await fixture.whenStable();
    
    expect(Object.keys(component.validationErrors).length).toBe(0);
    expect((component as any).submitting()).toBe(false);
  });
});
