import { TestBed } from '@angular/core/testing';
import { OvernightReports } from './overnight-reports';

describe('OvernightReports', () => {
  it('says the report is on the way', async () => {
    await TestBed.configureTestingModule({
      imports: [OvernightReports],
    }).compileComponents();
    const fixture = TestBed.createComponent(OvernightReports);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('h2')?.textContent).toContain('Overnight reports are on the way');
  });
});
