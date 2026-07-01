import { Component, OnInit } from '@angular/core';
import { DeviceService } from '../../services/device.service';
import { AcsMaxBox5G } from '../../models/device.model';
import { ChartConfiguration } from 'chart.js';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  totalDevices = 0;
  devices: AcsMaxBox5G[] = [];
  isLoading = false;

  // Chart data
  signalQualityChartData: ChartConfiguration<'bar'>['data'] = { labels: [], datasets: [] };
  throughputChartData: ChartConfiguration<'line'>['data'] = { labels: [], datasets: [] };
  deviceStatusChartData: ChartConfiguration<'doughnut'>['data'] = { labels: [], datasets: [] };

  // Chart options
  signalQualityChartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { display: true } }
  };

  throughputChartOptions: ChartConfiguration<'line'>['options'] = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { display: true } }
  };

  deviceStatusChartOptions: ChartConfiguration<'doughnut'>['options'] = {
    responsive: true,
    maintainAspectRatio: true,
    plugins: { legend: { display: true } }
  };

  constructor(private deviceService: DeviceService) { }

  ngOnInit(): void {
    this.loadDashboardData();
  }

  loadDashboardData(): void {
    this.isLoading = true;

    // Load total devices count
    this.deviceService.getTotalDevices().subscribe(
      (count) => {
        this.totalDevices = count;
      },
      (error) => console.error('Error loading total devices', error)
    );

    // Load all devices for charts
    this.deviceService.getAllDevices().subscribe(
      (data) => {
        this.devices = data;
        this.initializeCharts();
        this.isLoading = false;
      },
      (error) => {
        console.error('Error loading devices', error);
        this.isLoading = false;
      }
    );
  }

  initializeCharts(): void {
    this.initializeSignalQualityChart();
    this.initializeThroughputChart();
    this.initializeDeviceStatusChart();
  }

  initializeSignalQualityChart(): void {
    const sinrValues = this.devices
      .filter(d => d.sinr)
      .map(d => {
        const value = d.sinr?.replace('dB', '').trim();
        return parseInt(value || '0', 10);
      });

    const rsrpValues = this.devices
      .filter(d => d.rsrp)
      .map(d => {
        const value = d.rsrp?.replace('dBm', '').trim();
        return Math.abs(parseInt(value || '0', 10));
      });

    this.signalQualityChartData = {
      labels: ['SINR (avg)', 'RSRP (avg)'],
      datasets: [
        {
          label: 'Signal Quality',
          data: [
            sinrValues.length > 0 ? sinrValues.reduce((a, b) => a + b, 0) / sinrValues.length : 0,
            rsrpValues.length > 0 ? rsrpValues.reduce((a, b) => a + b, 0) / rsrpValues.length : 0
          ],
          backgroundColor: ['#007bff', '#28a745'],
          borderColor: ['#0056b3', '#218838'],
          borderWidth: 1
        }
      ]
    };
  }

  initializeThroughputChart(): void {
    const downlodValues = this.devices
      .filter(d => d.downlinkThroughput)
      .map(d => {
        const value = d.downlinkThroughput?.match(/\d+/)?.[0];
        return parseInt(value || '0', 10);
      });

    const uploadValues = this.devices
      .filter(d => d.uplinkThroughput)
      .map(d => {
        const value = d.uplinkThroughput?.match(/\d+/)?.[0];
        return parseInt(value || '0', 10);
      });

    const avgDownload = downlodValues.length > 0 ? downlodValues.reduce((a, b) => a + b, 0) / downlodValues.length : 0;
    const avgUpload = uploadValues.length > 0 ? uploadValues.reduce((a, b) => a + b, 0) / uploadValues.length : 0;

    this.throughputChartData = {
      labels: ['Downlink (Bytes/s)', 'Uplink (Bytes/s)'],
      datasets: [
        {
          label: 'Average Throughput',
          data: [avgDownload, avgUpload],
          borderColor: '#007bff',
          backgroundColor: 'rgba(0,123,255,0.1)',
          tension: 0.4,
          fill: true
        }
      ]
    };
  }

  initializeDeviceStatusChart(): void {
    const registeredCount = this.devices.filter(d => d.registered).length;
    const unregisteredCount = this.devices.length - registeredCount;

    this.deviceStatusChartData = {
      labels: ['Registered', 'Unregistered'],
      datasets: [
        {
          data: [registeredCount, unregisteredCount],
          backgroundColor: ['#28a745', '#dc3545'],
          borderColor: ['#218838', '#c82333'],
          borderWidth: 1
        }
      ]
    };
  }

  getOnlineDevices(): number {
    const now = new Date();
    const fiveMinutesAgo = new Date(now.getTime() - 5 * 60000);
    return this.devices.filter(d => d.lastInform && new Date(d.lastInform) > fiveMinutesAgo).length;
  }

  getOfflineDevices(): number {
    return this.totalDevices - this.getOnlineDevices();
  }
}
