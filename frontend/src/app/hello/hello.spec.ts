import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HelloService } from './hello';

describe('HelloService', () => {
  let http: HttpTestingController;
  let service: HelloService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(HelloService);
  });

  afterEach(() => http.verify());

  it('calls GET /api/hello and returns the message', () => {
    let result: { message: string } | undefined;
    service.getHello().subscribe((r) => (result = r));

    const req = http.expectOne('/api/hello');
    expect(req.request.method).toBe('GET');
    req.flush({ message: 'Hello from Reco-Resto' });

    expect(result).toEqual({ message: 'Hello from Reco-Resto' });
  });
});
