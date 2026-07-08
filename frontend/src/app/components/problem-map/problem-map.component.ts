import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { DeviceService } from '../../services/device.service';
import { IncidentWithDeviceInfo } from '../../models/device.model';
import * as L from 'leaflet';

export interface ProblemSite {
  latitude: number;
  longitude: number;
  incidents: IncidentWithDeviceInfo[];
  siteName: string;
}

@Component({
  selector: 'app-problem-map',
  templateUrl: './problem-map.component.html',
  styleUrls: ['./problem-map.component.css']
})
export class ProblemMapComponent implements OnInit, AfterViewInit, OnDestroy {
  incidents: IncidentWithDeviceInfo[] = [];
  problemSites: ProblemSite[] = [];
  isLoading = false;
  errorMessage = '';

  private map: L.Map | undefined;
  private markerLayer: L.LayerGroup = L.layerGroup();

  constructor(private deviceService: DeviceService) {
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
  }

  loadIncidents(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.deviceService.getIncidentsWithDeviceInfo().subscribe({
      next: (data) => {
        this.incidents = data;
        this.isLoading = false;
        this.groupBySite(data);
        setTimeout(() => {
          this.map?.invalidateSize();
          this.addMarkers();
        }, 100);
      },
      error: (error) => {
        console.error('Error loading incidents', error);
        this.errorMessage = 'Erreur lors du chargement des incidents.';
        this.isLoading = false;
      }
    });
  }

  private groupBySite(incidents: IncidentWithDeviceInfo[]): void {
    const siteMap = new Map<string, ProblemSite>();

    for (const inc of incidents) {
      if (!inc.latitude || !inc.longitude) continue;
      const key = `${inc.latitude.toFixed(4)}_${inc.longitude.toFixed(4)}`;
      const siteName = inc.cellName && inc.cellName.length >= 8
        ? inc.cellName.substring(0, 8)
        : 'Site inconnu';

      if (!siteMap.has(key)) {
        siteMap.set(key, { latitude: inc.latitude, longitude: inc.longitude, incidents: [], siteName });
      }
      siteMap.get(key)!.incidents.push(inc);
    }

    this.problemSites = Array.from(siteMap.values()).sort(
      (a, b) => b.incidents.length - a.incidents.length
    );
  }

  private addMarkers(): void {
    if (!this.map) return;
    this.markerLayer.clearLayers();

    if (this.problemSites.length === 0) return;

    const bounds = L.latLngBounds([]);

    for (const site of this.problemSites) {
      const color = site.incidents.length >= 5 ? '#dc3545' :
                    site.incidents.length >= 3 ? '#FF7900' : '#28a745';

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
      bounds.extend([site.latitude, site.longitude]);
    }

    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 13 });
    }
  }

  getTotalProblemSites(): number {
    return this.problemSites.length;
  }

  getTotalIncidents(): number {
    return this.incidents.length;
  }
}
