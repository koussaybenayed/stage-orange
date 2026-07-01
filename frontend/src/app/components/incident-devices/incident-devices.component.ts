import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DeviceService } from '../../services/device.service';
import { AcsMaxBox5G } from '../../models/device.model';

@Component({
  selector: 'app-incident-devices',
  templateUrl: './incident-devices.component.html',
  styleUrls: ['./incident-devices.component.css']
})
export class IncidentDevicesComponent implements OnInit {
  devices: AcsMaxBox5G[] = [];
  msisdn: number = 0;
  isLoading = false;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService
  ) { }

  ngOnInit(): void {
    this.route.paramMap.subscribe(params => {
      const msisdnStr = params.get('msisdn');
      if (msisdnStr) {
        this.msisdn = Number(msisdnStr);
        this.loadDevices();
      }
    });
  }

  loadDevices(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.deviceService.getDevicesByMsisdn(this.msisdn).subscribe(
      (data) => {
        this.devices = data;
        this.isLoading = false;
        if (data.length === 0) {
          this.errorMessage = 'Aucun appareil trouvé avec RSRP5G non null pour ce MSISDN.';
        }
      },
      (error) => {
        console.error('Error loading devices by MSISDN', error);
        this.errorMessage = 'Erreur lors du chargement des appareils.';
        this.isLoading = false;
      }
    );
  }

  goBack(): void {
    window.history.back();
  }
}
