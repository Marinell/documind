import { Component, OnInit, OnDestroy, ViewChild, ElementRef, ChangeDetectorRef, AfterViewInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, ChatMessage, StreamEvent } from '../api.service'; // Updated import
import { HttpEventType } from '@angular/common/http';
import { Subscription, catchError, of, tap } from 'rxjs';
import { Chart, registerables } from 'chart.js/auto'; // Import Chart.js
import { MarkdownModule } from 'ngx-markdown';
import { provideMarkdown } from 'ngx-markdown';


@Component({
  imports: [
    CommonModule,
    FormsModule,
    MarkdownModule
    // ApiService is a service, providedIn: 'root', so not needed here
    // RouterModule might be needed if it used routerLink, but it doesn't appear to.
  ],
  providers: [provideMarkdown()],
  selector: 'app-chat',
  templateUrl: './chat.component.html',
  styleUrls: ['./chat.component.css']
})
export class ChatComponent implements OnInit, OnDestroy, AfterViewInit {
  @ViewChild('chatMessagesContainer') private chatMessagesContainer!: ElementRef;
  @ViewChild('fileInput') fileInput!: ElementRef<HTMLInputElement>;
  @ViewChild('chartCanvas') chartCanvas!: ElementRef<HTMLCanvasElement>; // For Chart.js

  sessionId: string | null = null;
  messages: ChatMessage[] = [];
  newMessage: string = '';
  isLoading: boolean = false;
  uploadProgress: number | null = null;
  uploadError: string | null = null;
  currentError: string | null = null;
  currentChartData: any | null = null; // To store chart data
  private chartInstance: Chart | null = null; // To hold Chart.js instance
  isChartCollapsed: boolean = false;

  selectedFileName: string | null = null;
  uploadedDocuments: string[] = [];

  private streamSubscription: Subscription | null = null;

  constructor(private apiService: ApiService, private cdr: ChangeDetectorRef) {
    Chart.register(...registerables); // Register Chart.js components
  }

  ngOnInit(): void {
    this.startNewChat();
  }

  ngAfterViewInit(): void {
    // Chart.js canvas might not be available until AfterViewInit
    // We will initialize/update chart when data is received.
  }

  ngOnDestroy(): void {
    if (this.streamSubscription) {
      this.streamSubscription.unsubscribe();
    }
    // Optionally clear session on component destroy, or manage sessions more explicitly
    // if (this.sessionId) {
    //   this.apiService.clearSession(this.sessionId).subscribe();
    // }
  }

  startNewChat(): void {
    this.isLoading = true;
    this.currentError = null;
    this.currentChartData = null; // Clear previous chart
    if (this.chartInstance) {
      this.chartInstance.destroy();
      this.chartInstance = null;
    }

    if (this.sessionId) { // Clear previous session server-side
        this.apiService.clearSession(this.sessionId).pipe(
            tap(() => console.log(`Session ${this.sessionId} cleared.`)),
            catchError(err => {
                console.error('Error clearing previous session:', err);
                // Continue starting new session even if clearing old one fails
                return of(null);
            })
        ).subscribe(() => this.initiateNewSession());
    } else {
        this.initiateNewSession();
    }
  }

  private initiateNewSession(): void {
    this.apiService.startNewSession().subscribe({
      next: (id) => {
        this.sessionId = id;
        this.messages = [{ type: 'system', text: 'New chat session started. Upload documents to begin.', timestamp: new Date() }];
        this.isLoading = false;
        this.cdr.detectChanges(); // Ensure view updates
        console.log('New session ID:', this.sessionId);
      },
      error: (err) => {
        console.error('Error starting new session:', err);
        this.currentError = 'Failed to start a new chat session. Please try again.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  onFileSelected(event: Event): void {
    const element = event.target as HTMLInputElement;
    const file = element.files?.[0];

    if (file && this.sessionId) {
        this.selectedFileName = file.name;
      this.uploadProgress = 0;
      this.uploadError = null;
      this.currentError = null;
      this.isLoading = true;
      this.addMessage('system', `Uploading ${file.name}...`);

      this.apiService.uploadDocument(this.sessionId, file).subscribe({
        next: (httpEvent) => {
          if (httpEvent.type === HttpEventType.UploadProgress && httpEvent.total) {
            this.uploadProgress = Math.round(100 * httpEvent.loaded / httpEvent.total);
          } else if (httpEvent.type === HttpEventType.Response) {
            this.uploadProgress = null;
            this.addMessage('system', `${file.name} uploaded successfully. Ready to chat.`);
            this.uploadedDocuments.push(file.name);
            console.log('Upload complete:', httpEvent.body);
            this.isLoading = false;
            this.selectedFileName = null; // Reset after successful upload
          }
          this.cdr.detectChanges();
        },
        error: (err) => {
          console.error('Upload error:', err);
          this.uploadProgress = null;
          // err.error might contain the JSON response from the backend
          const errorMsg = err.error?.error || err.message || 'File upload failed.';
          this.uploadError = errorMsg;
          this.addMessage('error', `Failed to upload ${file.name}: ${errorMsg}`);
          this.isLoading = false;
          this.selectedFileName = null; // Reset on error
          this.cdr.detectChanges();
        },
        complete: () => {
            // Reset file input so same file can be re-uploaded if needed
            if (this.fileInput) {
                this.fileInput.nativeElement.value = '';
            }
        }
      });
    } else if (!this.sessionId) {
        this.currentError = "Cannot upload file: No active session. Try 'New Chat'.";
        this.addMessage('error', "Cannot upload file: No active session. Try 'New Chat'.");
    }
  }

  sendMessage(): void {
    if (!this.newMessage.trim() || !this.sessionId) {
      if(!this.sessionId) this.currentError = "No active session. Cannot send message.";
      return;
    }

    this.addMessage('user', this.newMessage);
    const currentAssistantMessage: ChatMessage = { type: 'assistant', text: '', timestamp: new Date() };
    this.messages.push(currentAssistantMessage);
    this.scrollToBottom();

    this.isLoading = true;
    this.currentError = null;
    const userMessageToSend = this.newMessage;
    this.newMessage = ''; // Clear input after sending


    // Ensure any previous stream is closed before starting a new one
    if (this.streamSubscription) {
        this.streamSubscription.unsubscribe();
    }

    this.streamSubscription = this.apiService.sendMessage(this.sessionId, userMessageToSend).subscribe({
      next: (event: StreamEvent) => {
        switch (event.type) {
          case 'token':
            currentAssistantMessage.text += event.data;
            break;
          case 'chart':
            this.currentChartData = event.data;
            this.renderChart();
            // Optionally add a system message like "Assistant provided a chart."
            // Or let the assistant's text explain it.
            break;
          case 'complete':
            this.isLoading = false;
            if (currentAssistantMessage.text.trim() === '' && !this.currentChartData) { // also check if chart was the only response
              this.messages.pop(); // Remove empty assistant message bubble
            }
            console.log('Stream complete event:', event.data);
            break;
          case 'error':
            const errorMsg = event.data?.message || event.data?.detail?.message || event.data?.error || 'An error occurred during streaming.';
            currentAssistantMessage.text = `Error: ${errorMsg}`;
            this.currentError = errorMsg;
            this.isLoading = false;
            break;
        }
        this.cdr.detectChanges();
        this.scrollToBottom();
      },
      error: (err) => { // Errors from the Observable itself (e.g. network, or if subject.error was called)
        console.error('Message stream error (observable):', err);
        const errorDetail = err.data?.message || err.data?.detail?.message || err.message || 'Failed to get response.';
        currentAssistantMessage.text = `Error: ${errorDetail}`;
        this.currentError = errorDetail;
        this.isLoading = false;
        this.cdr.detectChanges();
        this.scrollToBottom();
      },
      complete: () => { // Observable completion (when subject.complete is called)
        this.isLoading = false;
        // Ensure UI reflects final state, especially if last event was not 'complete' type
        if (currentAssistantMessage.text.trim() === '' && !this.currentChartData && this.messages[this.messages.length -1] === currentAssistantMessage) {
             this.messages.pop();
        }
        this.cdr.detectChanges();
        this.scrollToBottom();
        console.log('Message stream observable completed.');
      }
    });
  }

  renderChart(): void {
    // Defer chart rendering to the next tick to ensure canvas is in the DOM
    setTimeout(() => {
      if (!this.currentChartData || !this.chartCanvas) {
        console.warn('Chart data or canvas not available for rendering.');
        return;
      }

      if (this.chartInstance) {
        this.chartInstance.destroy(); // Destroy previous chart instance
      }

      const chartConfig: any = {
        type: this.currentChartData.chartType || 'bar', // 'bar', 'line', 'pie', etc.
        data: {
          labels: this.currentChartData.labels || [],
          datasets: this.currentChartData.datasets || []
        },
        options: {
          responsive: true,
          maintainAspectRatio: false, // Adjust as needed
          plugins: {
            title: {
              display: !!this.currentChartData.title,
              text: this.currentChartData.title
            },
            legend: {
              display: (this.currentChartData.datasets?.length > 1) // Show legend if multiple datasets
            }
          }
          // Add more Chart.js options as needed (scales, etc.)
        }
      };

      // Default colors if not provided by LLM (good practice)
      if (chartConfig.data.datasets) {
          const defaultColors = [
              { bg: 'rgba(54, 162, 235, 0.5)', border: 'rgba(54, 162, 235, 1)' }, // Blue
              { bg: 'rgba(255, 99, 132, 0.5)', border: 'rgba(255, 99, 132, 1)' }, // Red
              { bg: 'rgba(75, 192, 192, 0.5)', border: 'rgba(75, 192, 192, 1)' }, // Green
              { bg: 'rgba(255, 206, 86, 0.5)', border: 'rgba(255, 206, 86, 1)' }, // Yellow
              { bg: 'rgba(153, 102, 255, 0.5)', border: 'rgba(153, 102, 255, 1)' }, // Purple
          ];
          chartConfig.data.datasets.forEach((dataset: any, index: number) => {
              if (!dataset.backgroundColor) {
                  dataset.backgroundColor = defaultColors[index % defaultColors.length].bg;
              }
              if (!dataset.borderColor) {
                  dataset.borderColor = defaultColors[index % defaultColors.length].border;
              }
              if (!dataset.borderWidth) {
                   dataset.borderWidth = 1;
              }
          });
      }


      this.chartInstance = new Chart(this.chartCanvas.nativeElement, chartConfig);
      this.cdr.detectChanges(); // Ensure chart visibility updates
    }, 0);
  }

  addMessage(type: 'user' | 'assistant' | 'error' | 'system', text: string): void {
    this.messages.push({ type, text, timestamp: new Date() });
    this.scrollToBottom();
    this.cdr.detectChanges();
  }

  scrollToBottom(): void {
    try {
      // Needs a tick for the DOM to update, especially with ngFor
      setTimeout(() => {
        if (this.chatMessagesContainer) {
          this.chatMessagesContainer.nativeElement.scrollTop = this.chatMessagesContainer.nativeElement.scrollHeight;
        }
      }, 0);
    } catch (err) {
      console.error('Error scrolling to bottom:', err);
    }
  }

    hasUploadedDocuments() {
        return this.uploadedDocuments.length > 0;
    }

    toggleChartCollapse(): void {
        this.isChartCollapsed = !this.isChartCollapsed;
    }

    closeChart(): void {
        this.currentChartData = null;
        this.isChartCollapsed = false; // Reset collapse state
        if (this.chartInstance) {
            this.chartInstance.destroy();
            this.chartInstance = null;
        }
    }
}
