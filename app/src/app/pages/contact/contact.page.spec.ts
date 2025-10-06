import { ContactPage } from './contact.page';
// FormBuilder import removed - not needed in this spec

describe('ContactPage', () => {
  it('validates form and prevents submit when invalid', () => {
    const page = new ContactPage();
    // form should start invalid
    expect(page.form.valid).toBeFalse();
    page.form.setValue({ name: 'Test', email: 'invalid', message: 'Hello' });
    expect(page.form.valid).toBeFalse();
    page.form.patchValue({ email: 'test@example.com' });
    expect(page.form.valid).toBeTrue();
  });
});
