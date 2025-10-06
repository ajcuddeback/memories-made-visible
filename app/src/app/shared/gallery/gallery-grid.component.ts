import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface Photo {
  id: string;
  title?: string;
  alt?: string;
  url: string;
  thumbUrl?: string;
}

@Component({
  selector: 'gallery-grid',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './gallery-grid.component.html',
  styleUrls: ['./gallery-grid.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class GalleryGridComponent {
  @Input() photos: Photo[] = [];
  @Output() photoClick = new EventEmitter<number>();

  click(i: number) {
    this.photoClick.emit(i);
  }
}
