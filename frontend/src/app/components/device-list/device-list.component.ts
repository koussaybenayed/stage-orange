import { Component, OnInit } from '@angular/core';
import { DeviceService } from '../../services/device.service';
import { Router } from '@angular/router';
import { AcsMaxBox5G } from '../../models/device.model';

@Component({
  selector: 'app-device-list',
  templateUrl: './device-list.component.html',
  styleUrls: ['./device-list.component.css']
})
export class DeviceListComponent implements OnInit {
  devices: AcsMaxBox5G[] = [];
  totalElements = 0;
  currentPage = 0;
  pageSize = 10;
  isLoading = false;
  searchTerm = '';

  constructor(private deviceService: DeviceService, private router: Router) { }

  ngOnInit(): void {
    this.loadDevices();
  }

  loadDevices(): void {
    this.isLoading = true;
    if (this.searchTerm.trim()) {
      this.deviceService.searchDevices(this.searchTerm, this.currentPage, this.pageSize)
        .subscribe(
          (response) => {
            this.devices = response.content;
            this.totalElements = response.totalElements;
            this.isLoading = false;
          },
          (error) => {
            console.error('Error loading devices', error);
            this.isLoading = false;
          }
        );
    } else {
      this.deviceService.getDevices(this.currentPage, this.pageSize)
        .subscribe(
          (response) => {
            this.devices = response.content;
            this.totalElements = response.totalElements;
            this.isLoading = false;
          },
          (error) => {
            console.error('Error loading devices', error);
            this.isLoading = false;
          }
        );
    }
  }

  onSearch(): void {
    this.currentPage = 0;
    this.loadDevices();
  }

  onPageChange(page: number): void {
    this.currentPage = page;
    this.loadDevices();
  }

  viewDetails(id: number | undefined): void {
    if (id) {
      this.router.navigate(['/devices', id]);
    }
  }

  deleteDevice(id: number | undefined): void {
    if (id && confirm('Are you sure you want to delete this device?')) {
      this.deviceService.deleteDevice(id).subscribe(
        () => {
          this.loadDevices();
        },
        (error) => console.error('Error deleting device', error)
      );
    }
  }

  getTotalPages(): number {
    return Math.ceil(this.totalElements / this.pageSize);
  }
}
