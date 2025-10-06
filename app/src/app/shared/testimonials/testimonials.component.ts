import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface Testimonial {
  quote: string;
  author: string;
  context?: string;
}

@Component({
  selector: 'app-testimonials',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './testimonials.component.html',
  styleUrls: ['./testimonials.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TestimonialsComponent {
  @Input() testimonials?: Testimonial[];

  private readonly fallback: Testimonial[] = [
    { quote: 'They captured our wedding perfectly — every emotion and detail.', author: 'Samantha R.', context: 'Wedding, 2024' },
    { quote: 'We got family photos that feel like art. Highly recommend!', author: 'Miguel & Ana', context: 'Family Session' },
    { quote: 'Professional, friendly, and patient with our kids — love the results.', author: 'The Parkers', context: 'Family Session' },
  ];

  protected get items() {
    return this.testimonials?.length ? this.testimonials : this.fallback;
  }
}
