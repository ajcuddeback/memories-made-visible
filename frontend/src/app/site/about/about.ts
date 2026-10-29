import { SiteDataService } from '../../core/site-data.service';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PhotoSlot } from '../../shared/photo-slot';

@Component({
  selector: 'app-about',
  imports: [RouterLink, PhotoSlot],
  templateUrl: './about.html',
  styleUrl: './about.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AboutPage {
  readonly contact = inject(SiteDataService).contact;}
