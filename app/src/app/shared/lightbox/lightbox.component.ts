import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, HostListener } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface LightboxPhoto {
  id: string;
  title?: string;
  alt?: string;
  url: string;
  thumbUrl?: string;
}

@Component({
  selector: 'app-lightbox',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './lightbox.component.html',
  styleUrls: ['./lightbox.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LightboxComponent {
  @Input() photos: LightboxPhoto[] = [];
  @Input() openIndex: number | null = null;
  @Output() openIndexChange = new EventEmitter<number | null>();

  get isOpen() {
    return this.openIndex !== null && this.openIndex >= 0 && this.openIndex < this.photos.length;
  }

  close() {
    this.openIndex = null;
    this.openIndexChange.emit(null);
  }

  next() {
    if (!this.isOpen) return;
    this.openIndex = (this.openIndex! + 1) % this.photos.length;
    this.openIndexChange.emit(this.openIndex);
  }

  prev() {
    if (!this.isOpen) return;
    this.openIndex = (this.openIndex! - 1 + this.photos.length) % this.photos.length;
    this.openIndexChange.emit(this.openIndex);
  }

  @HostListener('window:keydown', ['$event'])
  handleKey(e: KeyboardEvent) {
    if (!this.isOpen) return;
    if (e.key === 'Escape') this.close();
    if (e.key === 'ArrowRight') this.next();
    if (e.key === 'ArrowLeft') this.prev();
  }
}
