import { RouterModule, Routes } from '@angular/router';
import { ChatComponent } from './chat/chat.component'; // Import the ChatComponent
import { NgModule } from '@angular/core';

export const routes: Routes = [
  // Define your routes here later
  // Example: { path: 'dashboard', component: DashboardComponent }
  { path: 'chat', component: ChatComponent },
  { path: '', redirectTo: '/chat', pathMatch: 'full' } // Default route
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
