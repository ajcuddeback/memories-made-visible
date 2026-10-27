import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Access, TeamMember } from '../../core/models';
import { AdminStore } from '../admin-store.service';
import { AuthService } from '../auth.service';

export const ACCESS_LABEL: Record<Access, string> = { OWNER: 'Owner', ADMIN: 'Admin', EMPLOYEE: 'Employee' };
export const ACCESS_HELP: Record<Access, string> = {
  OWNER: 'The one main account. Can do everything, including managing admins.',
  ADMIN: 'Everything in the studio: pricing, photos, publishing, insights, approvals, and adding employees.',
  EMPLOYEE: 'Day-to-day only: work the sessions pipeline, block time on the calendar, record pop-up print sales. No prices, photos, revenue or team.',
};

@Component({
  selector: 'app-team',
  imports: [DatePipe],
  templateUrl: './team.html',
  styleUrl: './team.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamSection {
  readonly store = inject(AdminStore);
  readonly auth = inject(AuthService);
  readonly members = signal<TeamMember[]>([]);
  readonly notice = signal('');
  readonly busy = signal(false);
  readonly label = ACCESS_LABEL;
  readonly help = ACCESS_HELP;

  readonly addOpen = signal(false);
  readonly form = signal({ name: '', role: '', email: '', access: 'EMPLOYEE' as Access });
  readonly formError = signal('');
  readonly cantAdd = computed(() => { const f = this.form(); return !f.name.trim() || !f.role.trim() || !/\S+@\S+\.\S+/.test(f.email); });
  /** Admins may hand out Employee or Admin; nobody hands out Owner. */
  readonly grantable: Access[] = ['EMPLOYEE', 'ADMIN'];

  constructor() { this.reload(); }

  private reload(): void {
    this.store.team().subscribe({ next: (m) => this.members.set(m), error: () => this.notice.set('Couldn’t load the team.') });
  }
  val(e: Event): string { return (e.target as HTMLInputElement | HTMLSelectElement).value; }
  setForm(field: 'name' | 'role' | 'email', e: Event): void { const v = this.val(e); this.form.update((f) => ({ ...f, [field]: v })); }
  setFormAccess(e: Event): void { const v = this.val(e) as Access; this.form.update((f) => ({ ...f, access: v })); }

  /** Can the signed-in person change this member? Admins can't touch the owner or other admins. */
  canEdit(m: TeamMember): boolean {
    if (m.isYou || m.access === 'OWNER') return false;
    return this.auth.isOwner() || m.access === 'EMPLOYEE';
  }

  add(): void {
    if (this.cantAdd() || this.busy()) return;
    const f = this.form();
    this.busy.set(true); this.formError.set('');
    this.store.addMember({ name: f.name.trim(), role: f.role.trim(), email: f.email.trim(), access: f.access }).subscribe({
      next: (m) => {
        this.busy.set(false); this.addOpen.set(false);
        this.form.set({ name: '', role: '', email: '', access: 'EMPLOYEE' });
        this.members.update((list) => [...list, m]);
        this.say(`${m.name} added. An email with a temporary password is on its way to ${m.email}.`);
      },
      error: (e) => { this.busy.set(false); this.formError.set(e?.error?.message ?? 'Couldn’t add that person.'); },
    });
  }

  setAccess(m: TeamMember, e: Event): void {
    const access = this.val(e) as Access;
    this.store.updateMember(m.name, { access }).subscribe({
      next: (u) => { this.replace(u); this.say(`${u.name} is now ${this.label[u.access].toLowerCase()}.`); },
      error: (err) => { this.say(err?.error?.message ?? 'Couldn’t change that.'); this.reload(); },
    });
  }
  toggleActive(m: TeamMember): void {
    const active = !m.active;
    if (!active && !confirm(`Deactivate ${m.name}? They won't be able to sign in until you reactivate them.`)) return;
    this.store.updateMember(m.name, { active }).subscribe({
      next: (u) => { this.replace(u); this.say(`${u.name} ${u.active ? 'can sign in again' : 'is deactivated'}.`); },
      error: (err) => this.say(err?.error?.message ?? 'Couldn’t change that.'),
    });
  }
  setBookable(m: TeamMember, bookable: boolean): void {
    this.store.updateMember(m.name, { bookable }).subscribe({
      next: (u) => { this.replace(u); this.say(bookable ? `${u.name} is now offered on the booking page.` : `${u.name} is no longer offered on the booking page.`); },
      error: (err) => { this.say(err?.error?.message ?? 'Couldn’t change that.'); this.reload(); },
    });
  }
  reinvite(m: TeamMember): void {
    if (!confirm(`Send ${m.name} a new temporary password? Their current password will stop working.`)) return;
    this.store.reinvite(m.name).subscribe({
      next: (u) => { this.replace(u); this.say(`A new temporary password was emailed to ${u.email}.`); },
      error: (err) => this.say(err?.error?.message ?? 'Couldn’t send that.'),
    });
  }
  remove(m: TeamMember): void {
    if (!confirm(`Remove ${m.name}'s account? This can't be undone (you can deactivate instead).`)) return;
    this.store.removeMember(m.name).subscribe({
      next: () => { this.members.update((list) => list.filter((x) => x.name !== m.name)); this.say(`${m.name} removed.`); },
      error: (err) => this.say(err?.error?.message ?? 'Couldn’t remove that account.'),
    });
  }

  private replace(u: TeamMember): void { this.members.update((list) => list.map((x) => (x.name === u.name ? u : x))); }
  private say(text: string): void { this.notice.set(text); setTimeout(() => this.notice.set(''), 7000); }
}
