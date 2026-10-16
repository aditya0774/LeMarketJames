import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../../environments/environment';
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

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/auth/register`);
    expect(req.request.method).toBe('POST');
    req.flush({ success: true, message: 'Registration successful' });

    await fixture.whenStable();
    
    expect(Object.keys(component.validationErrors).length).toBe(0);
    expect((component as any).submitting()).toBe(false);
  });

  describe('four-step flow', () => {
    const step = () => (component as any).step() as number;
    const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

    it('starts on "About you" and shows only the fields of that step', () => {
      fixture.detectChanges();

      expect(step()).toBe(1);
      expect(text()).toContain('STEP 1 OF 4');
      expect(fixture.nativeElement.querySelector('#firstName')).not.toBeNull();
      expect(fixture.nativeElement.querySelector('#streetAddress')).toBeNull();
    });

    it('stays on a step with errors and reports only the fields of that step', () => {
      expect(component.nextStep()).toBe(false);

      expect(step()).toBe(1);
      expect(component.validationErrors['firstName']).toBeTruthy();
      expect(component.validationErrors['streetAddress']).toBeFalsy();
    });

    it('moves forward once a step is valid, and back again', () => {
      component.registerData = { ...validFormData, username: 'jane_doe' };

      expect(component.nextStep()).toBe(true);
      expect(component.nextStep()).toBe(true);
      expect(step()).toBe(3);

      component.previousStep();
      expect(step()).toBe(2);
    });

    it('submits through Continue until the last step', () => {
      component.registerData = { ...validFormData, username: 'jane_doe' };
      (component as any).step.set(3);

      (component as any).continueOrSubmit();
      expect(step()).toBe(4);

      (component as any).continueOrSubmit();
      httpMock.expectOne(`${environment.apiBaseUrl}/api/auth/register`).flush({ success: true });
    });

    it('sends the user back to the first step with a schema error', () => {
      component.registerData = { ...validFormData, username: 'jane_doe', zipCode: '12' };
      (component as any).step.set(4);

      component.onSubmit();

      expect(step()).toBe(2);
      expect(component.validationErrors['zipCode']).toBeTruthy();
    });

    it('sends the user back to the step a server field error belongs to', async () => {
      component.registerData = { ...validFormData, username: 'jane_doe' };
      (component as any).step.set(4);

      component.onSubmit();
      httpMock
        .expectOne(`${environment.apiBaseUrl}/api/auth/register`)
        .flush({ errors: { email: 'Email is already registered.' } }, { status: 400, statusText: 'Bad Request' });
      await fixture.whenStable();

      expect(step()).toBe(1);
      expect(component.validationErrors['email']).toBe('Email is already registered.');
    });

    it('offers "Experienced" only from a $100,000 deposit', () => {
      component.registerData.initialDeposit = '5000';
      component.selectExperience('experienced');
      expect(component.registerData.investmentExperience).toBe('beginner');

      component.registerData.initialDeposit = '100000';
      component.selectExperience('experienced');
      expect(component.registerData.investmentExperience).toBe('experienced');
    });

    it('ticks off the password rules as they are met', () => {
      component.registerData.password = 'abcdefgh';
      (component as any).step.set(4);
      fixture.detectChanges();

      const rules = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('.pw-rules li'));
      expect(rules.map((li) => li.classList.contains('met'))).toEqual([true, false, true, false]);
    });
  });
});
