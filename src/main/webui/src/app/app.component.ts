import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { CommonModule } from '@angular/common'; // Import CommonModule

@Component({
  standalone: true,
  imports: [
    CommonModule, // For common directives like ngIf, ngFor
    RouterOutlet  // For <router-outlet>
  ],
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'Document Analyzer';
}
