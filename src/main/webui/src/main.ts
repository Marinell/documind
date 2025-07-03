import { bootstrapApplication } from '@angular/platform-browser';
import { AppComponent } from './app/app.component';
import { provideRouter } from '@angular/router';
import { appRoutes } from './app/app.routes'; // Will create this next
import { provideHttpClient } from '@angular/common/http'; // Import HttpClient provider

bootstrapApplication(AppComponent, {
  providers: [
    provideRouter(appRoutes),
    provideHttpClient() // Add HttpClient provider here
  ]
}).catch(err => console.error(err));
