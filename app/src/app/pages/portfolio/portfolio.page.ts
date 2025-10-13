import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GalleryGridComponent } from '../../shared/gallery/gallery-grid.component';
import { ImageService } from '../../services/image.service';
import { LightboxComponent } from '../../shared/lightbox/lightbox.component';

@Component({
  selector: 'portfolio-page',
  standalone: true,
  imports: [CommonModule, GalleryGridComponent, LightboxComponent],
  templateUrl: './portfolio.page.html',
  styleUrls: ['./portfolio.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioPage {
  photos: any[] = [];
  openIndex: number | null = null;

  constructor(private readonly imageService: ImageService) {
    this.photos = this.imageService.getAll();
    console.log('Portfolio photos:', this.photos.map(p => p.url));
  }

  open(index: number) {
    this.openIndex = index;
  }
}
