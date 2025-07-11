import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppComponent } from './app.component';
import { AppRoutingModule } from './app.routes';
import { ChatComponent } from './chat/chat.component';
import { FormsModule } from '@angular/forms';
import { HttpClientModule } from '@angular/common/http';

@NgModule({
  declarations: [
    // AppComponent and ChatComponent are now standalone, so they are removed from declarations.
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    FormsModule, // FormsModule can remain here if other non-standalone components in this module need it
    HttpClientModule, // HttpClientModule can remain here
    AppComponent,    // Import standalone AppComponent
    ChatComponent    // Import standalone ChatComponent
  ],
  providers: [],
  bootstrap: [AppComponent] // AppComponent is standalone, but still bootstrapped
})
export class AppModule { }
