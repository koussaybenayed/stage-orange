import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DeviceService } from '../../services/device.service';
import { IncidentWithDeviceInfo } from '../../models/device.model';

@Component({
  selector: 'app-device-list',
  templateUrl: './device-list.component.html',
  styleUrls: ['./device-list.component.css']
})
export class DeviceListComponent implements OnInit {
  incidents: IncidentWithDeviceInfo[] = [];
  isLoading = false;

  constructor(private deviceService: DeviceService, private router: Router) { }

  ngOnInit(): void {
    this.loadIncidents();
  }

  loadIncidents(): void {
    this.isLoading = true;
    this.deviceService.getIncidentsWithDeviceInfo().subscribe(
      (data) => {
        this.incidents = data;
        this.isLoading = false;
      },
      (error) => {
        console.error('Error loading incidents', error);
        this.isLoading = false;
      }
    );
  }

  viewDevices(msisdn: number): void {
    this.router.navigate(['/devices/by-msisdn', msisdn]);
  }
}
