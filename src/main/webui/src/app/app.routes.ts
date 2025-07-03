import { Routes } from '@angular/router';
import { ChatComponent } from './chat/chat.component'; // Import the ChatComponent

export const appRoutes: Routes = [
  // Define your routes here later
  // Example: { path: 'dashboard', component: DashboardComponent }
  { path: 'chat', component: ChatComponent },
  { path: '', redirectTo: '/chat', pathMatch: 'full' } // Default route
];
