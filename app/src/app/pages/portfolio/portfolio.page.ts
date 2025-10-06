import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { GalleryGridComponent } from '../../shared/gallery/gallery-grid.component';
import { ImageService } from '../../services/image.service';

@Component({
  selector: 'portfolio-page',
  standalone: true,
  imports: [CommonModule, GalleryGridComponent],
  templateUrl: './portfolio.page.html',
  styleUrls: ['./portfolio.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PortfolioPage {
  photos: any[] = [];

  constructor(private readonly imageService: ImageService) {
    this.photos = this.imageService.getAll();
    console.log('Portfolio photos:', this.photos.map(p => p.url));
  }
}
