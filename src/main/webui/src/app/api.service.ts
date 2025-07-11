import { Injectable } from '@angular/core';
import { HttpClient, HttpEvent, HttpEventType, HttpRequest } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { map, catchError, tap } from 'rxjs/operators';

export interface SessionResponse {
  sessionId: string;
}

export interface UploadResponse {
  message: string;
  // Add other relevant fields from backend if any
}

export interface ChatMessage {
  type: 'user' | 'assistant' | 'error' | 'system';
  text: string;
  timestamp: Date;
}

export interface StreamEvent {
  type: 'token' | 'chart' | 'complete' | 'error';
  data?: any;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private apiUrl = '/api/chat';

  constructor(private http: HttpClient) { }

  startNewSession(): Observable<string> {
    return this.http.post<SessionResponse>(`${this.apiUrl}/new`, {}).pipe(
      map(response => response.sessionId),
      catchError(this.handleError<string>('startNewSession'))
    );
  }

  clearSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${sessionId}`).pipe(
      catchError(this.handleError<void>('clearSession'))
    );
  }

  uploadDocument(sessionId: string, file: File): Observable<HttpEvent<UploadResponse>> {
    const formData: FormData = new FormData();
    formData.append('file', file, file.name);

    const req = new HttpRequest('POST', `${this.apiUrl}/${sessionId}/upload`, formData, {
      reportProgress: true, // For upload progress tracking if needed
    });

    return this.http.request<UploadResponse>(req).pipe(
      // Cast the event type if necessary, or ensure the backend response matches UploadResponse for relevant HttpEventTypes
      map(event => event as HttpEvent<UploadResponse>), // Add a cast here
      catchError(this.handleError<HttpEvent<UploadResponse>>('uploadDocument'))
    );
  }

  // Note: Backend ChatResource.sendMessage was adjusted to GET for SSE.
  // If it's POST, then EventSource cannot be used directly like this.
  // The fetch-based approach (commented out earlier) would be needed for POST + SSE.
  // For now, assuming GET /.../messageStream?message=... from previous decision.
  sendMessage(sessionId: string, message: string): Observable<StreamEvent> {
    const subject = new Subject<StreamEvent>();

    // Check: The backend ChatResource's @POST /message was NOT changed to GET.
    // It still expects POST with a JSON body.
    // EventSource cannot make POST requests with a body.
    // THEREFORE, the fetch-based approach for SSE is REQUIRED.
    // I will switch to that now.

    // const eventSourceUrl = `${this.apiUrl}/${sessionId}/messageStream?message=${encodeURIComponent(message)}`;
    // const es = new EventSource(eventSourceUrl); // THIS WON'T WORK WITH THE CURRENT POST BACKEND

    // Using Fetch API to handle SSE with POST request
    fetch(`${this.apiUrl}/${sessionId}/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: message })
    }).then(response => {
      if (!response.ok) {
        response.json().then(errData => { // Try to parse error from backend
          subject.next({ type: 'error', data: errData || { message: `HTTP error! status: ${response.status}` } });
          subject.complete();
        }).catch(() => { // Fallback if error response is not JSON
          subject.next({ type: 'error', data: { message: `HTTP error! status: ${response.status}` } });
          subject.complete();
        });
        // Do not proceed with reading stream if response is not ok
        return Promise.reject(new Error(`HTTP error! status: ${response.status}`));
      }
      if (!response.body) {
        const noBodyError = new Error('No response body from server.');
        subject.next({ type: 'error', data: { message: noBodyError.message } });
        subject.complete();
        return Promise.reject(noBodyError); // Explicitly return the rejection
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      function processText(text: string) {
        buffer += text;
        let eolIndex;
        // SSE messages are separated by double newlines (\n\n)
        while ((eolIndex = buffer.indexOf('\n\n')) >= 0) {
          const message = buffer.substring(0, eolIndex);
          buffer = buffer.substring(eolIndex + 2); // +2 for \n\n

          let eventType = 'message'; // Default SSE event type
          let eventData = '';

          const lines = message.split('\n');
          for (const line of lines) {
            if (line.startsWith('event:')) {
              eventType = line.substring('event:'.length).trim();
            } else if (line.startsWith('data:')) {
              eventData = line.substring('data:'.length).trim();
            }
          }

          try {
            const parsedData = JSON.parse(eventData); // Backend sends JSON in 'data' field
            if (eventType === 'chart') {
              subject.next({ type: 'chart', data: parsedData });
            } else if (eventType === 'complete') { // Backend sends event: complete
              subject.next({ type: 'complete', data: parsedData });
              subject.complete();
              reader.cancel(); // Stop reading from stream
              return; // Exit processing loop
            } else if (eventType === 'error') { // Backend sends event: error
              subject.next({ type: 'error', data: parsedData });
              subject.complete();
              reader.cancel();
              return;
            } else { // Default 'message' event, or unknown from backend (treat as token)
              // The backend now sends {"type": "token", "data": "actual_token_string"}
              // So parsedData should be this object. We extract the actual token.
              if (parsedData && parsedData.type === 'token') {
                 subject.next({ type: 'token', data: parsedData.data });
              } else {
                // Fallback if data is not in expected token structure
                subject.next({ type: 'token', data: parsedData });
              }
            }
          } catch (e) {
            console.error('Error parsing SSE data JSON:', e, 'Raw data:', eventData);
            // If data is not JSON, treat as simple string token (though backend should always send JSON now)
            if (eventType === 'message') { // Only for default message events
                 subject.next({ type: 'token', data: eventData });
            }
          }
        }
      }

      function push() {
        reader.read().then(({ done, value }) => {
          if (done) {
            if (buffer.length > 0) { // Process any remaining buffered text
              processText(buffer); // This might be an incomplete message
              buffer = '';
            }
            // If not already completed by an 'event: complete'
            if (!subject.closed) {
                subject.next({ type: 'complete', data: { message: 'Stream finished (done)'} });
                subject.complete();
            }
            return;
          }
          processText(decoder.decode(value, { stream: true }));
          if (!subject.closed) { // Continue reading only if subject is not closed
            push();
          }
        }).catch(err => {
          if (!subject.closed) {
            subject.next({ type: 'error', data: { message: 'Stream reading error.', detail: err} });
            subject.complete();
          }
        });
      }
      push();
      return;
    }).catch(err => { // Catch for the initial fetch itself (e.g. network error)
        if (!subject.closed) {
          subject.next({ type: 'error', data: { message: 'Failed to connect to stream.', detail: err } });
          subject.complete();
        }
    });

    return subject.asObservable();
  }


  private handleError<T>(operation = 'operation', result?: T) {
    return (error: any): Observable<T> => {
      console.error(`${operation} failed:`, error);
      // Let the app keep running by returning an empty result or rethrow
      // For now, rethrow the error so the component can handle it
      throw error;
    };
  }
}
