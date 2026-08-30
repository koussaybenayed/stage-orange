import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AcsMaxBox5G, HzDailySeries, HzError, HzMsisdnStats, Incident, IncidentOverview, IncidentWithDeviceInfo, NameCount, NearbySite, PageResponse, TopZonesResponse } from '../models/device.model';
import { environment } from '../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class DeviceService {
  private baseUrl = environment.apiUrl;
  private apiUrl = `${this.baseUrl}/devices`;
  private incidentUrl = `${this.baseUrl}/incidents`;

  constructor(private http: HttpClient) { }

  getDevices(page: number = 0, size: number = 10, sort: string = 'timestamp,desc'): Observable<PageResponse<AcsMaxBox5G>> {
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

  getIncidentsWithDeviceInfo(msisdn?: number): Observable<IncidentWithDeviceInfo[]> {
    const params = msisdn ? new HttpParams().set('msisdn', msisdn.toString()) : undefined;
    return this.http.get<IncidentWithDeviceInfo[]>(`${this.incidentUrl}/with-device-info`, { params });
  }

  getDevicesByMsisdn(msisdn: number): Observable<AcsMaxBox5G[]> {
    return this.http.get<AcsMaxBox5G[]>(`${this.apiUrl}/by-msisdn/${msisdn}`);
  }

  getIncidentOverview(): Observable<IncidentOverview> {
    return this.http.get<IncidentOverview>(`${this.incidentUrl}/stats/overview`);
  }

  getIncidentStatsByType(): Observable<NameCount[]> {
    return this.http.get<NameCount[]>(`${this.incidentUrl}/stats/by-type`);
  }

  getIncidentStatsByOffre(): Observable<NameCount[]> {
    return this.http.get<NameCount[]>(`${this.incidentUrl}/stats/by-offre`);
  }

  getIncidentStatsByDate(): Observable<NameCount[]> {
    return this.http.get<NameCount[]>(`${this.incidentUrl}/stats/by-date`);
  }

  getHzErrorDistribution(): Observable<NameCount[]> {
    return this.http.get<NameCount[]>(`${this.incidentUrl}/stats/hzerror`);
  }

  getHzDailyEvolution(apn?: string): Observable<HzDailySeries[]> {
    const params = new HttpParams().set('apn', apn ?? '');
    return this.http.get<HzDailySeries[]>(`${this.incidentUrl}/hz/daily-evolution`, { params });
  }

  getHzOffers(): Observable<NameCount[]> {
    return this.http.get<NameCount[]>(`${this.incidentUrl}/hz/offers`);
  }

  getHzMsisdnStats(msisdn: number, dateFrom?: string, dateTo?: string): Observable<HzMsisdnStats> {
    let params = new HttpParams();
    if (dateFrom) params = params.set('dateFrom', dateFrom);
    if (dateTo) params = params.set('dateTo', dateTo);
    return this.http.get<HzMsisdnStats>(`${this.incidentUrl}/hz/msisdn/${msisdn}`, { params });
  }

  getHzMsisdnDailyEvolution(msisdn: number, dateFrom?: string, dateTo?: string): Observable<HzDailySeries[]> {
    let params = new HttpParams();
    if (dateFrom) params = params.set('dateFrom', dateFrom);
    if (dateTo) params = params.set('dateTo', dateTo);
    return this.http.get<HzDailySeries[]>(`${this.incidentUrl}/hz/msisdn/${msisdn}/daily-evolution`, { params });
  }

  getHzErrors(date: string, status: string, apn?: string, limit: number = 200): Observable<HzError[]> {
    let params = new HttpParams()
      .set('date', date)
      .set('status', status)
      .set('limit', limit.toString());
    if (apn) {
      params = params.set('apn', apn);
    }
    return this.http.get<HzError[]>(`${this.incidentUrl}/hz/errors`, { params });
  }

  getTopZones(limit: number = 10): Observable<TopZonesResponse> {
    const params = new HttpParams().set('limit', limit.toString());
    return this.http.get<TopZonesResponse>(`${this.incidentUrl}/top-zones`, { params });
  }

  getNearbySites(lat: number, lng: number, radius: number = 5000, date?: string): Observable<NearbySite[]> {
    let params = new HttpParams()
      .set('lat', lat.toString())
      .set('lng', lng.toString())
      .set('radius', radius.toString());
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<NearbySite[]>(`${this.incidentUrl}/nearby-sites`, { params });
  }
}
