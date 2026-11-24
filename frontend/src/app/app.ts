import { Component, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HelloService } from './hello/hello';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly message = signal<string | null>(null);
  protected readonly failed = signal(false);

  constructor() {
    inject(HelloService)
      .getHello()
      .subscribe({
        next: (response) => this.message.set(response.message),
        error: () => this.failed.set(true),
      });
  }
}
