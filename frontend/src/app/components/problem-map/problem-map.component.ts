import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { timeout } from 'rxjs/operators';
import { DeviceService } from '../../services/device.service';
import { IncidentWithDeviceInfo } from '../../models/device.model';
import * as L from 'leaflet';

export interface ProblemSite {
  latitude: number;
  longitude: number;
  incidents: IncidentWithDeviceInfo[];
  siteName: string;
}

export type ZoneSeverity = 'high' | 'medium' | 'low';

export interface TopZone {
  siteName: string;
  count: number;
  severity: ZoneSeverity;
  latitude: number;
  longitude: number;
}

@Component({
  selector: 'app-problem-map',
  templateUrl: './problem-map.component.html',
  styleUrls: ['./problem-map.component.css']
})
export class ProblemMapComponent implements OnInit, AfterViewInit, OnDestroy {
  allIncidents: IncidentWithDeviceInfo[] = [];
  incidents: IncidentWithDeviceInfo[] = [];
  problemSites: ProblemSite[] = [];
  topZones: TopZone[] = [];

  filterType: 'all' | 'hzerror' = 'all';

  readonly topZonesCount = 10;

  isLoading = false;
  errorMessage = '';
  focusedTarget = '';

  private map: L.Map | undefined;
  private markerLayer: L.LayerGroup = L.layerGroup();
  private pulseLayer: L.LayerGroup = L.layerGroup();
  private markersByKey = new Map<string, L.Marker>();

  constructor(
    private deviceService: DeviceService,
    private route: ActivatedRoute
  ) {
    L.Marker.prototype.options.icon = L.icon({
      iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png',
      iconRetinaUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png',
      shadowUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png',
      iconSize: [25, 41],
      iconAnchor: [12, 41],
      popupAnchor: [1, -34],
      shadowSize: [41, 41]
    });
  }

  ngOnInit(): void {
    this.loadIncidents();
  }

  ngAfterViewInit(): void {
    this.initMap();
  }

  ngOnDestroy(): void {
    if (this.map) {
      this.map.remove();
      this.map = undefined;
    }
  }

  private initMap(): void {
    const el = document.getElementById('problem-map');
    if (!el) return;

    this.map = L.map('problem-map', {
      zoomControl: true,
      attributionControl: false
    }).setView([36.8, 10.2], 7);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19
    }).addTo(this.map);

    this.markerLayer.addTo(this.map);
    this.pulseLayer.addTo(this.map);
  }

  loadIncidents(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.deviceService.getIncidentsWithDeviceInfo()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => {
          this.isLoading = false;
          try {
            this.allIncidents = data;
            this.applyFilter();
            setTimeout(() => {
              this.map?.invalidateSize();
              this.renderMarkers(true);
              this.handleNavigationTarget();
            }, 100);
          } catch (err) {
            console.error('Error processing incidents', err);
            this.errorMessage = 'Erreur lors du traitement des incidents.';
          }
        },
        error: (error) => {
          console.error('Error loading incidents', error);
          this.errorMessage = 'Erreur lors du chargement des incidents.';
          this.isLoading = false;
        }
      });
  }

  onFilterTypeChange(): void {
    this.applyFilter();
    this.renderMarkers(true);
  }

  hasHzError(inc: IncidentWithDeviceInfo): boolean {
    const hz = inc.hzError;
    return !!hz && hz.trim() !== '' && hz !== 'No HZ errors';
  }

  private filteredIncidents(): IncidentWithDeviceInfo[] {
    if (this.filterType === 'hzerror') {
      return this.allIncidents.filter(inc => this.hasHzError(inc));
    }
    return this.allIncidents;
  }

  private applyFilter(): void {
    this.incidents = this.filteredIncidents();
    this.groupBySite(this.incidents);
    this.buildTopZones();
  }

  private siteNameOf(inc: IncidentWithDeviceInfo): string {
    return inc.cellName && inc.cellName.length >= 8
      ? inc.cellName.substring(0, 8)
      : 'Site inconnu';
  }

  private groupBySite(incidents: IncidentWithDeviceInfo[]): void {
    const siteMap = new Map<string, ProblemSite>();

    for (const inc of incidents) {
      if (!inc.latitude || !inc.longitude) continue;
      const key = `${inc.latitude.toFixed(4)}_${inc.longitude.toFixed(4)}`;
      const siteName = this.siteNameOf(inc);

      if (!siteMap.has(key)) {
        siteMap.set(key, { latitude: inc.latitude, longitude: inc.longitude, incidents: [], siteName });
      }
      siteMap.get(key)!.incidents.push(inc);
    }

    this.problemSites = Array.from(siteMap.values()).sort(
      (a, b) => b.incidents.length - a.incidents.length
    );
  }

  private buildTopZones(): void {
    this.topZones = this.problemSites
      .slice(0, this.topZonesCount)
      .map(site => ({
        siteName: site.siteName,
        count: site.incidents.length,
        severity: this.severityOf(site.incidents.length),
        latitude: site.latitude,
        longitude: site.longitude
      }));
  }

  severityOf(count: number): ZoneSeverity {
    return count >= 5 ? 'high' : count >= 3 ? 'medium' : 'low';
  }

  markerColorOf(site: ProblemSite): string {
    return site.incidents.length >= 5 ? '#dc3545' :
           site.incidents.length >= 3 ? '#FF7900' : '#28a745';
  }

  flyToZone(zone: TopZone): void {
    this.map?.flyTo([zone.latitude, zone.longitude], 13, { duration: 0.6 });
  }

  private renderMarkers(fit: boolean): void {
    if (!this.map) return;
    this.markerLayer.clearLayers();
    this.markersByKey.clear();

    if (this.problemSites.length === 0) return;

    const bounds = L.latLngBounds([]);

    for (const site of this.problemSites) {
      const key = `${site.latitude.toFixed(4)}_${site.longitude.toFixed(4)}`;
      const color = this.markerColorOf(site);

      const icon = L.divIcon({
        className: 'problem-marker',
        html: `<div class="marker-pin" style="background:${color}">
               <span>${site.incidents.length}</span></div>`,
        iconSize: [40, 40],
        iconAnchor: [20, 40],
        popupAnchor: [0, -45]
      });

      const sujetList = site.incidents.map(inc =>
        `<li><b>${inc.requestNumber}</b>: ${inc.sujet}</li>`
      ).join('');

      const popupContent = `
        <div class="popup-content">
          <h4>${site.siteName}</h4>
          <p><b>${site.incidents.length} incident(s)</b></p>
          <ul style="margin:0;padding-left:1.2rem">${sujetList}</ul>
        </div>
      `;

      const marker = L.marker([site.latitude, site.longitude], { icon })
        .bindPopup(popupContent, { maxWidth: 350 });

      this.markerLayer.addLayer(marker);
      this.markersByKey.set(key, marker);
      bounds.extend([site.latitude, site.longitude]);
    }

    if (fit && bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 13 });
    }
  }

  private handleNavigationTarget(): void {
    const params = this.route.snapshot.queryParamMap;
    const lat = params.get('lat');
    const lng = params.get('lng');
    const incident = params.get('incident');
    const site = params.get('site');
    if (!lat || !lng) return;

    const key = `${Number(lat).toFixed(4)}_${Number(lng).toFixed(4)}`;
    this.focusedTarget = incident && incident !== 'null'
      ? `${site && site !== 'null' ? site : 'Site'} · ${incident}`
      : (site && site !== 'null' ? site : '');
    this.highlightSite(key);
  }

  private highlightSite(key: string): void {
    if (!this.map) return;
    const site = this.problemSites.find(s => `${s.latitude.toFixed(4)}_${s.longitude.toFixed(4)}` === key);
    if (!site) return;

    const marker = this.markersByKey.get(key);
    this.map.flyTo([site.latitude, site.longitude], 15, { duration: 0.7 });

    setTimeout(() => {
      marker?.openPopup();
      this.pulseLayer.clearLayers();
      this.pulseLayer.addLayer(L.marker([site.latitude, site.longitude], {
        icon: L.divIcon({
          className: 'pulse-icon',
          html: '<div class="pulse-ring"></div><div class="pulse-dot"></div>',
          iconSize: [48, 48],
          iconAnchor: [24, 24]
        })
      }));
    }, 800);
  }

  clearFocus(): void {
    this.focusedTarget = '';
    this.pulseLayer.clearLayers();
  }

  getTotalProblemSites(): number {
    return this.problemSites.length;
  }

  getTotalIncidents(): number {
    return this.incidents.length;
  }

  getHzErrorCount(): number {
    return this.allIncidents.filter(inc => this.hasHzError(inc)).length;
  }
}
