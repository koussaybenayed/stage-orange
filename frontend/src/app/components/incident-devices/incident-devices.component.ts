import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { DeviceService } from '../../services/device.service';
import { AcsMaxBox5G, IncidentWithDeviceInfo, NearbySite } from '../../models/device.model';
import * as L from 'leaflet';

@Component({
  selector: 'app-incident-devices',
  templateUrl: './incident-devices.component.html',
  styleUrls: ['./incident-devices.component.css']
})
export class IncidentDevicesComponent implements OnInit, AfterViewInit, OnDestroy {
  devices: AcsMaxBox5G[] = [];
  incidents: IncidentWithDeviceInfo[] = [];
  nearbySites: NearbySite[] = [];
  msisdn: number = 0;
  isLoading = false;
  errorMessage = '';
  showMap = false;

  private map: L.Map | undefined;
  private markerLayer: L.LayerGroup = L.layerGroup();
  private incidentLayer: L.LayerGroup = L.layerGroup();
  private nearbyLayer: L.LayerGroup = L.layerGroup();
  private siteLayer: L.LayerGroup = L.layerGroup();
  private paramSub: Subscription | undefined;

  skeletonRows = Array(5).fill(0);
  skeletonCols = Array(8).fill(0);

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService
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
    this.paramSub = this.route.paramMap.subscribe(params => {
      const msisdnStr = params.get('msisdn');
      if (msisdnStr) {
        this.msisdn = Number(msisdnStr);
        this.loadDevices();
      }
    });
  }

  ngAfterViewInit(): void {
    const el = document.getElementById('device-map');
    if (!el) return;

    this.map = L.map('device-map', {
      zoomControl: true,
      attributionControl: false
    }).setView([36.8, 10.2], 7);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19
    }).addTo(this.map);

    this.markerLayer.addTo(this.map);
    this.incidentLayer.addTo(this.map);
    this.nearbyLayer.addTo(this.map);
    this.siteLayer.addTo(this.map);

    this.map.invalidateSize();
    this.addMarkers();
    this.addIncidentMarkers();
    this.addNearbyMarkers();
    this.addSiteMarkers();
  }

  ngOnDestroy(): void {
    this.paramSub?.unsubscribe();
    if (this.map) {
      this.map.remove();
      this.map = undefined;
    }
  }

  loadDevices(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.showMap = false;
    this.markerLayer.clearLayers();
    this.incidentLayer.clearLayers();
    this.nearbyLayer.clearLayers();
    this.siteLayer.clearLayers();

    this.deviceService.getIncidentsWithDeviceInfo(this.msisdn)
      .pipe(timeout(60000))
      .subscribe({
        next: (incidents) => {
          this.incidents = incidents;
          this.refreshShowMap();
          this.addIncidentMarkers();
          this.addSiteMarkers();
          const located = incidents.find(i => i.x && i.y);
          if (located && located.y && located.x) {
            const day = located.created ? located.created.substring(0, 10) : undefined;
            this.deviceService.getNearbySites(located.y, located.x, 5000, day)
              .pipe(timeout(60000))
              .subscribe({
                next: (sites) => {
                  this.nearbySites = sites;
                  this.addNearbyMarkers();
                },
                error: (err) => {
                  console.error('Error loading nearby sites', err);
                }
              });
          }
        },
        error: (err) => {
          console.error('Error loading incident for MSISDN', err);
        }
      });

    this.deviceService.getDevicesByMsisdn(this.msisdn)
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => {
          this.isLoading = false;
          try {
            this.devices = data;
            this.refreshShowMap();
            if (!this.map) return;
            setTimeout(() => {
              this.map?.invalidateSize();
              this.addMarkers();
              this.addIncidentMarkers();
            }, 100);
          } catch (err) {
            console.error('Error processing devices', err);
            this.errorMessage = 'Erreur lors du traitement des appareils.';
          }
        },
        error: (error) => {
          console.error('Error loading devices by MSISDN', error);
          this.errorMessage = 'Erreur lors du chargement des appareils.';
          this.isLoading = false;
        }
      });
  }

  private refreshShowMap(): void {
    const hasDeviceLocation = this.devices.some(d => d.latitude && d.longitude);
    const hasIncidentLocation = this.incidents.some(i => i.x && i.y);
    this.showMap = hasDeviceLocation || hasIncidentLocation;
  }

  private addMarkers(): void {
    if (!this.map) return;
    this.markerLayer.clearLayers();

    const firstIncident = this.incidents.find(i => i.x && i.y);
    const clientLat = firstIncident ? firstIncident.y! : null;
    const clientLng = firstIncident ? firstIncident.x! : null;

    const devicesWithLocation = this.devices.filter(d => clientLat != null ? true : (d.latitude && d.longitude));
    if (devicesWithLocation.length === 0) return;

    const bounds = L.latLngBounds([]);
    let idx = 0;
    for (const device of devicesWithLocation) {
      const offset = (idx % 6) * 0.0004;
      const lat = clientLat != null ? clientLat + offset : device.latitude!;
      const lng = clientLng != null ? clientLng : device.longitude!;
      idx++;
      const marker = L.marker([lat, lng]);
      const cellInfo = device.cellName ? `<br/><b>Cellule:</b> ${device.cellName}` : '';
      marker.bindPopup(
        `<b>${device.serialNumber}</b>${cellInfo}<br/>` +
        `<b>IP:</b> ${device.ip}<br/>` +
        `<b>RSRP5G:</b> ${device.rsrp5G || '-'}<br/>` +
        `<b>SINR5G:</b> ${device.sinr5G || '-'}`
      );
      this.markerLayer.addLayer(marker);
      bounds.extend([lat, lng]);
    }

    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
    }
  }

  private addIncidentMarkers(): void {
    if (!this.map) return;
    this.incidentLayer.clearLayers();

    const locatedIncidents = this.incidents.filter(i => i.x && i.y);
    if (locatedIncidents.length === 0) return;

    const bounds = L.latLngBounds([]);
    for (const inc of locatedIncidents) {
      const lat = inc.y!;
      const lng = inc.x!;

      const icon = L.divIcon({
        className: 'incident-marker',
        html: '<div class="incident-pin"></div>',
        iconSize: [20, 20],
        iconAnchor: [10, 20],
        popupAnchor: [0, -22]
      });

      const period = inc.incidentPeriod ? `<br/><b>Période:</b> ${inc.incidentPeriod}` : '';
      const marker = L.marker([lat, lng], { icon })
        .bindPopup(
          `<b>Incident ${inc.requestNumber}</b><br/>` +
          `<b>Site:</b> ${inc.siteCode || inc.cellName || '-'}${period}`,
          { maxWidth: 350 }
        );

      this.incidentLayer.addLayer(marker);

      const radius = L.circle([lat, lng], {
        radius: 5000,
        color: '#dc3545',
        weight: 2,
        fillColor: '#dc3545',
        fillOpacity: 0.12,
        dashArray: '6 6'
      });
      this.incidentLayer.addLayer(radius);

      bounds.extend([lat, lng]);
    }

    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 13 });
    }
  }

  private addSiteMarkers(): void {
    if (!this.map) return;
    this.siteLayer.clearLayers();

    const locatedSites = this.incidents.filter(i => i.latitude && i.longitude);
    if (locatedSites.length === 0) return;

    for (const inc of locatedSites) {
      const icon = L.divIcon({
        className: 'site-marker',
        html: '<div class="site-pin"></div>',
        iconSize: [24, 24],
        iconAnchor: [12, 12],
        popupAnchor: [0, -16]
      });

      const marker = L.marker([inc.latitude!, inc.longitude!], { icon })
        .bindPopup(
          `<b>Site ${inc.siteCode || inc.cellName || '-'}</b>`,
          { maxWidth: 350 }
        );

      this.siteLayer.addLayer(marker);
    }
  }

  private addNearbyMarkers(): void {
    if (!this.map) return;
    this.nearbyLayer.clearLayers();

    const allSites = this.nearbySites;
    if (allSites.length === 0) return;

    for (const site of allSites) {
      const hasIncident = site.hasIncident;
      const icon = L.divIcon({
        className: hasIncident ? 'nearby-marker' : 'site-nearby-marker',
        html: hasIncident ? '<div class="nearby-pin"></div>' : '<div class="nearby-pin-plain"></div>',
        iconSize: [16, 16],
        iconAnchor: [8, 16],
        popupAnchor: [0, -18]
      });

      const period = site.incidentPeriod ? `<br/><b>Réclamation:</b> ${site.incidentPeriod}` : '';
      const tech = site.incidentTech ? `<br/><b>Services:</b> ${site.incidentTech}` : '';
      const marker = L.marker([site.latitude, site.longitude], { icon })
        .bindPopup(
          `<b>Site ${site.site}</b>${period}${tech}`,
          { maxWidth: 350 }
        );

      this.nearbyLayer.addLayer(marker);

      const radius = L.circle([site.latitude, site.longitude], {
        radius: 5000,
        color: hasIncident ? '#fd7e14' : '#64748b',
        weight: hasIncident ? 1 : 1,
        fillColor: hasIncident ? '#fd7e14' : '#64748b',
        fillOpacity: hasIncident ? 0.06 : 0.02,
        dashArray: hasIncident ? '4 4' : '2 4'
      });
      this.nearbyLayer.addLayer(radius);
    }
  }

  goBack(): void {
    window.history.back();
  }
}
