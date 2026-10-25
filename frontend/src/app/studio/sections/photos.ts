import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { AdminStore } from '../admin-store.service';
import { Icon } from '../../shared/icons';

const MAX_BYTES = 25 * 1024 * 1024;

@Component({
  selector: 'app-photos',
  imports: [Icon],
  templateUrl: './photos.html',
  styleUrl: './photos.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PhotosSection {
  readonly store = inject(AdminStore);
  readonly busy = signal<Set<string>>(new Set());
  readonly dragOver = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  isBusy(slot: string): boolean {
    return this.busy().has(slot);
  }

  pick(slot: string, input: HTMLInputElement): void {
    input.value = '';
    input.onchange = () => {
      const f = input.files?.[0];
      if (f) this.upload(slot, f);
    };
    input.click();
  }

  onDragOver(slot: string, e: DragEvent): void {
    e.preventDefault();
    this.dragOver.set(slot);
  }

  onDragLeave(): void {
    this.dragOver.set(null);
  }

  onDrop(slot: string, e: DragEvent): void {
    e.preventDefault();
    this.dragOver.set(null);
    const f = e.dataTransfer?.files?.[0];
    if (f) this.upload(slot, f);
  }

  upload(slot: string, file: File): void {
    this.error.set(null);
    if (!file.type.startsWith('image/')) {
      this.error.set('Please choose an image file (JPEG, PNG, WebP or AVIF).');
      return;
    }
    if (file.size > MAX_BYTES) {
      this.error.set('That photo is over 25 MB. Export a smaller version and try again.');
      return;
    }
    this.mark(slot, true);
    this.store.uploadPhoto(slot, file).subscribe({
      next: (res) => {
        this.store.setPhotoUrl(slot, res.url);
        this.mark(slot, false);
      },
      error: (err) => {
        this.error.set(err?.error?.message ?? 'Upload failed. Please try again.');
        this.mark(slot, false);
      },
    });
  }

  clear(slot: string, label: string): void {
    if (!confirm(`Remove the photo from "${label}"?`)) return;
    this.mark(slot, true);
    this.store.clearPhoto(slot).subscribe({
      next: () => {
        this.store.setPhotoUrl(slot, null);
        this.mark(slot, false);
      },
      error: () => {
        this.error.set('Couldn’t remove that photo. Please try again.');
        this.mark(slot, false);
      },
    });
  }

  private mark(slot: string, on: boolean): void {
    this.busy.update((s) => {
      const n = new Set(s);
      if (on) n.add(slot); else n.delete(slot);
      return n;
    });
  }
}
