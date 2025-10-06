import { ChangeDetectionStrategy, Component } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'services-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './services.page.html',
  styleUrls: ['./services.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServicesPage {}
