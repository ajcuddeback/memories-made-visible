import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';
// RouterLink removed (not used in this template)
import { HeroComponent } from '../../shared/hero/hero.component';
import { GalleryGridComponent } from '../../shared/gallery/gallery-grid.component';
import { TestimonialsComponent } from '../../shared/testimonials/testimonials.component';
import { ImageService } from '../../services/image.service';

@Component({
  selector: 'home-page',
  standalone: true,
  imports: [CommonModule, HeroComponent, GalleryGridComponent, TestimonialsComponent],
  templateUrl: './home.page.html',
  styleUrls: ['./home.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  photos: any[] = [];
  testimonials: { quote: string; author: string; context?: string }[] = [];

  constructor(private readonly imageService: ImageService) {
    this.photos = this.imageService.getFeatured();
    this.testimonials = [
      { quote: 'They captured our wedding perfectly — every emotion and detail.', author: 'Samantha R.', context: 'Wedding, 2024' },
      { quote: 'We got family photos that feel like art. Highly recommend!', author: 'Miguel & Ana', context: 'Family Session' },
      { quote: 'Professional, friendly, and patient with our kids — love the results.', author: 'The Parkers', context: 'Family Session' },
    ];
    // debug: log resolved photo urls
    console.log('Home photos:', this.photos.map(p => p.url));
  }

  open(index: number) {
    // placeholder: will integrate lightbox later
    console.log('Open photo', index, this.photos[index]);
  }
}
