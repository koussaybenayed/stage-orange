import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { ChartOptions } from 'chart.js';
import { DeviceService } from '../../services/device.service';
import { ThemeService } from '../../services/theme.service';
import { IncidentOverview, NameCount, TopZonesResponse } from '../../models/device.model';
import { hzErrorTranslation } from '../../models/hz-error-translations';

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit, OnDestroy {
  overview: IncidentOverview | null = null;
  topZones: TopZonesResponse | null = null;

  loadingOverview = true;
  loadingZones = true;
  loadingType = true;
  loadingOffre = true;
  loadingDate = true;
  loadingHz = true;

  errorMessage = '';

  typeChartData: any = { labels: [], datasets: [] };
  offreChartData: any = { labels: [], datasets: [] };
  zoneChartData: any = { labels: [], datasets: [] };
  dateChartData: any = { labels: [], datasets: [] };
  hzChartData: any = { labels: [], datasets: [] };

  typeChartOptions: ChartOptions = {};
  offreChartOptions: ChartOptions = {};
  zoneChartOptions: ChartOptions = {};
  dateChartOptions: ChartOptions = {};
  hzChartOptions: ChartOptions = {};

  skeletonStats = Array(4).fill(0);
  skeletonCharts = Array(5).fill(0);

  private palette = ['#FF7900', '#28a745', '#0d6efd', '#dc3545', '#6f42c1', '#20c997', '#fd7e14', '#d63384', '#17a2b8', '#e83e8c'];
  private themeSub: Subscription | undefined;

  constructor(
    private deviceService: DeviceService,
    private themeService: ThemeService
  ) { }

  ngOnInit(): void {
    this.themeSub = this.themeService.theme$.subscribe(() => this.applyChartTheme());
    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.themeSub?.unsubscribe();
  }

  loadDashboard(): void {
    this.errorMessage = '';
    this.loadingOverview = true;
    this.loadingZones = true;
    this.loadingType = true;
    this.loadingOffre = true;
    this.loadingDate = true;
    this.loadingHz = true;

    this.deviceService.getIncidentOverview()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => { this.overview = data; this.loadingOverview = false; this.applyChartTheme(); },
        error: (err) => { console.error('Overview error', err); this.loadingOverview = false; }
      });

    this.deviceService.getTopZones(10)
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => { this.topZones = data; this.loadingZones = false; this.buildZoneChart(); this.applyChartTheme(); },
        error: (err) => { console.error('Top zones error', err); this.loadingZones = false; }
      });

    this.deviceService.getIncidentStatsByType()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => { this.loadingType = false; this.buildTypeChart(data); this.applyChartTheme(); },
        error: (err) => { console.error('By type error', err); this.loadingType = false; }
      });

    this.deviceService.getIncidentStatsByOffre()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => { this.loadingOffre = false; this.buildOffreChart(data); this.applyChartTheme(); },
        error: (err) => { console.error('By offre error', err); this.loadingOffre = false; }
      });

    this.deviceService.getIncidentStatsByDate()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => { this.loadingDate = false; this.buildDateChart(data); this.applyChartTheme(); },
        error: (err) => { console.error('By date error', err); this.loadingDate = false; }
      });

    this.deviceService.getHzErrorDistribution()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => { this.loadingHz = false; this.buildHzChart(data); this.applyChartTheme(); },
        error: (err) => { console.error('HZ error distribution error', err); this.loadingHz = false; }
      });
  }

  get totalIncidents(): number { return this.overview?.totalIncidents ?? 0; }
  get totalSites(): number { return this.topZones?.totalSites ?? 0; }
  get lastDay(): number { return this.overview?.lastDay ?? 0; }
  get last7Days(): number { return this.overview?.last7Days ?? 0; }

  private buildTypeChart(data: NameCount[]): void {
    this.typeChartData = {
      labels: data.map(d => `${d.name} · ${d.count}`),
      datasets: [{
        data: data.map(d => d.count),
        backgroundColor: this.palette.slice(0, data.length),
        borderWidth: 0,
        hoverOffset: 6
      }]
    };
  }

  private buildOffreChart(data: NameCount[]): void {
    this.offreChartData = {
      labels: data.map(d => `${d.name} · ${d.count}`),
      datasets: [{
        data: data.map(d => d.count),
        backgroundColor: this.palette.slice(0, data.length),
        borderWidth: 0,
        hoverOffset: 6
      }]
    };
  }

  private buildZoneChart(): void {
    const zones = this.topZones?.zones ?? [];
    this.zoneChartData = {
      labels: zones.map(z => z.name),
      datasets: [{
        data: zones.map(z => z.count),
        backgroundColor: this.palette,
        borderRadius: 4
      }]
    };
  }

  private buildDateChart(data: NameCount[]): void {
    this.dateChartData = {
      labels: data.map(d => d.name),
      datasets: [{
        data: data.map(d => d.count),
        borderColor: '#FF7900',
        backgroundColor: 'rgba(255,121,0,0.12)',
        fill: true,
        tension: 0.4,
        pointRadius: 2,
        pointHoverRadius: 5,
        pointBackgroundColor: '#FF7900'
      }]
    };
  }

  private buildHzChart(data: NameCount[]): void {
    const top = data.slice(0, 8);
    this.hzChartData = {
      labels: top.map(d => `${hzErrorTranslation(d.name) ?? d.name} · ${d.count}`),
      datasets: [{
        data: top.map(d => d.count),
        backgroundColor: this.palette.slice(0, top.length),
        borderRadius: 4
      }]
    };
  }

  private chartColors(): { text: string; text2: string; grid: string; tooltipBg: string } {
    const css = getComputedStyle(document.documentElement);
    const read = (variable: string, fallback: string): string => {
      const val = css.getPropertyValue(variable).trim();
      return val || fallback;
    };
    return {
      text: read('--text', '#e8e8ec'),
      text2: read('--text-2', '#a8adbd'),
      grid: read('--border', '#2a2d3a'),
      tooltipBg: read('--surface-2', '#242634')
    };
  }

  private applyChartTheme(): void {
    const c = this.chartColors();
    const legend = { labels: { color: c.text, boxWidth: 12, padding: 12, font: { size: 11 } } };
    const tooltip = {
      backgroundColor: c.tooltipBg,
      titleColor: c.text,
      bodyColor: c.text2,
      borderColor: c.grid,
      borderWidth: 1
    };

    this.typeChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      color: c.text,
      plugins: { legend, tooltip }
    };

    this.offreChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      color: c.text,
      plugins: { legend, tooltip }
    };

    this.zoneChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      color: c.text,
      plugins: { legend: { display: false }, tooltip },
      scales: {
        x: { ticks: { color: c.text2 }, grid: { color: c.grid } },
        y: { ticks: { color: c.text2 }, grid: { display: false } }
      }
    };

    this.dateChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      color: c.text,
      plugins: { legend: { display: false }, tooltip },
      scales: {
        x: { ticks: { color: c.text2, maxTicksLimit: 10 }, grid: { color: c.grid } },
        y: { ticks: { color: c.text2 }, grid: { color: c.grid } }
      }
    };

    this.hzChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      indexAxis: 'y',
      color: c.text,
      plugins: { legend: { display: false }, tooltip },
      scales: {
        x: { ticks: { color: c.text2 }, grid: { color: c.grid } },
        y: { ticks: { color: c.text2 }, grid: { display: false } }
      }
    };
  }
}
