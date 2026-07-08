import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { DeviceService } from '../../services/device.service';
import { AcsMaxBox5G } from '../../models/device.model';
import * as L from 'leaflet';

@Component({
  selector: 'app-incident-devices',
  templateUrl: './incident-devices.component.html',
  styleUrls: ['./incident-devices.component.css']
})
export class IncidentDevicesComponent implements OnInit, AfterViewInit, OnDestroy {
  devices: AcsMaxBox5G[] = [];
  msisdn: number = 0;
  isLoading = false;
  errorMessage = '';
  showMap = false;

  private map: L.Map | undefined;
  private markerLayer: L.LayerGroup = L.layerGroup();
  private paramSub: Subscription | undefined;

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

    this.deviceService.getDevicesByMsisdn(this.msisdn).subscribe({
      next: (data) => {
        this.devices = data;
        this.isLoading = false;
        if (data.length === 0) {
          this.errorMessage = 'Aucun appareil trouvé avec RSRP5G non null pour ce MSISDN.';
          return;
        }
        const hasLocation = data.some(d => d.latitude && d.longitude);
        this.showMap = hasLocation;
        if (!hasLocation || !this.map) return;
        setTimeout(() => {
          this.map?.invalidateSize();
          this.addMarkers();
        }, 100);
      },
      error: (error) => {
        console.error('Error loading devices by MSISDN', error);
        this.errorMessage = 'Erreur lors du chargement des appareils.';
        this.isLoading = false;
      }
    });
  }

  private addMarkers(): void {
    if (!this.map) return;
    this.markerLayer.clearLayers();

    const devicesWithLocation = this.devices.filter(d => d.latitude && d.longitude);
    if (devicesWithLocation.length === 0) return;

    const bounds = L.latLngBounds([]);
    for (const device of devicesWithLocation) {
      const marker = L.marker([device.latitude!, device.longitude!]);
      const cellInfo = device.cellName ? `<br/><b>Cellule:</b> ${device.cellName}` : '';
      marker.bindPopup(
        `<b>${device.serialNumber}</b>${cellInfo}<br/>` +
        `<b>IP:</b> ${device.ip}<br/>` +
        `<b>RSRP5G:</b> ${device.rsrp5G || '-'}<br/>` +
        `<b>SINR5G:</b> ${device.sinr5G || '-'}`
      );
      this.markerLayer.addLayer(marker);
      bounds.extend([device.latitude!, device.longitude!]);
    }

    if (bounds.isValid()) {
      this.map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
    }
  }

  goBack(): void {
    window.history.back();
  }
}
