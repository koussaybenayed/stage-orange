import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { timeout } from 'rxjs/operators';
import { DeviceService } from '../../services/device.service';
import { AcsMaxBox5G } from '../../models/device.model';

@Component({
  selector: 'app-device-detail',
  templateUrl: './device-detail.component.html',
  styleUrls: ['./device-detail.component.css']
})
export class DeviceDetailComponent implements OnInit {
  device: AcsMaxBox5G | null = null;
  isLoading = false;
  isEditing = false;
  editForm: AcsMaxBox5G = { serialNumber: '', ip: '', version: '' };

  constructor(
    private deviceService: DeviceService,
    private route: ActivatedRoute,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.loadDeviceDetails();
  }

  loadDeviceDetails(): void {
    const id = this.route.snapshot.params['id'];
    if (id) {
      this.isLoading = true;
      this.deviceService.getDeviceById(id)
        .pipe(timeout(30000))
        .subscribe(
          (data) => {
            this.device = data;
            this.editForm = { ...data };
            this.isLoading = false;
          },
          (error) => {
            console.error('Error loading device', error);
            this.isLoading = false;
            this.router.navigate(['/devices']);
          }
        );
    }
  }

  toggleEdit(): void {
    this.isEditing = !this.isEditing;
    if (!this.isEditing && this.device) {
      this.editForm = { ...this.device };
    }
  }

  saveChanges(): void {
    if (this.device?.id) {
      this.isLoading = true;
      this.deviceService.updateDevice(this.device.id, this.editForm).subscribe(
        (data) => {
          this.device = data;
          this.isEditing = false;
          this.isLoading = false;
        },
        (error) => {
          console.error('Error updating device', error);
          this.isLoading = false;
        }
      );
    }
  }

  goBack(): void {
    this.router.navigate(['/devices']);
  }

  deleteDevice(): void {
    if (this.device?.id && confirm('Are you sure you want to delete this device?')) {
      this.deviceService.deleteDevice(this.device.id).subscribe(
        () => {
          this.router.navigate(['/devices']);
        },
        (error) => console.error('Error deleting device', error)
      );
    }
  }
}
