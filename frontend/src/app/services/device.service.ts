import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AcsMaxBox5G, Incident, PageResponse } from '../models/device.model';

@Injectable({
  providedIn: 'root'
})
export class DeviceService {
  private apiUrl = 'http://localhost:8081/api/devices';
  private incidentUrl = 'http://localhost:8081/api/incidents';

  constructor(private http: HttpClient) { }

  getDevices(page: number = 0, size: number = 10, sort: string = 'lastInform,desc'): Observable<PageResponse<AcsMaxBox5G>> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString())
      .set('sort', sort);

    return this.http.get<PageResponse<AcsMaxBox5G>>(this.apiUrl, { params });
  }

  searchDevices(searchTerm: string, page: number = 0, size: number = 10): Observable<PageResponse<AcsMaxBox5G>> {
    const params = new HttpParams()
      .set('searchTerm', searchTerm)
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<PageResponse<AcsMaxBox5G>>(`${this.apiUrl}/search`, { params });
  }

  getDeviceById(id: number): Observable<AcsMaxBox5G> {
    return this.http.get<AcsMaxBox5G>(`${this.apiUrl}/${id}`);
  }

  createDevice(device: AcsMaxBox5G): Observable<AcsMaxBox5G> {
    return this.http.post<AcsMaxBox5G>(this.apiUrl, device);
  }

  updateDevice(id: number, device: AcsMaxBox5G): Observable<AcsMaxBox5G> {
    return this.http.put<AcsMaxBox5G>(`${this.apiUrl}/${id}`, device);
  }

  deleteDevice(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getTotalDevices(): Observable<number> {
    return this.http.get<number>(`${this.apiUrl}/stats/total`);
  }

  getAllDevices(): Observable<AcsMaxBox5G[]> {
    return this.http.get<AcsMaxBox5G[]>(`${this.apiUrl}/all`);
  }

  getIncidents(): Observable<Incident[]> {
    return this.http.get<Incident[]>(this.incidentUrl);
  }

  getDevicesByMsisdn(msisdn: number): Observable<AcsMaxBox5G[]> {
    return this.http.get<AcsMaxBox5G[]>(`${this.apiUrl}/by-msisdn/${msisdn}`);
  }
}
