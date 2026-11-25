import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { HelloService } from './hello/hello';

@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  templateUrl: './app.html',
})
export class App {}
