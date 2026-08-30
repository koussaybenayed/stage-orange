import { Component, OnDestroy, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { ChartOptions } from 'chart.js';
import { DeviceService } from '../../services/device.service';
import { ThemeService } from '../../services/theme.service';
import { HzDailySeries, HzMsisdnStats, NameCount } from '../../models/device.model';

@Component({
  selector: 'app-home-zone',
  templateUrl: './home-zone.component.html',
  styleUrls: ['./home-zone.component.css']
})
export class HomeZoneComponent implements OnInit, OnDestroy {
  series: HzDailySeries[] = [];
  charts: any[] = [];
  offers: NameCount[] = [];
  selectedOffer = 'all';

  msisdnInput = '';
  dateFrom = '';
  dateTo = '';
  msisdnStats: HzMsisdnStats | null = null;
  msisdnSeries: HzDailySeries[] = [];
  msisdnCharts: any[] = [];
  msisdnLoading = false;
  msisdnError = '';

  loading = true;
  errorMessage = '';

  skeletonCharts = Array(6).fill(0);

  private palette = ['#FF7900', '#28a745', '#0d6efd', '#dc3545', '#6f42c1', '#20c997', '#fd7e14', '#d63384', '#17a2b8', '#e83e8c'];
  private themeSub: Subscription | undefined;

  constructor(
    private deviceService: DeviceService,
    private themeService: ThemeService,
    private router: Router
  ) { }

ngOnInit(): void {
    this.themeSub = this.themeService.theme$.subscribe(() => {
      this.applyChartTheme();
      this.applyMsisdnChartTheme();
    });
    this.loadDashboard();
    this.loadOffers();
  }

  ngOnDestroy(): void {
    this.themeSub?.unsubscribe();
  }

onOfferChange(): void {
    this.loadDashboard();
  }

  onMsisdnSubmit(): void {
    const raw = this.msisdnInput.trim();
    if (!raw) {
      this.msisdnError = 'Veuillez saisir un MSISDN.';
      return;
    }
    const msisdn = Number(raw);
    if (!Number.isInteger(msisdn) || msisdn <= 0) {
      this.msisdnError = 'MSISDN invalide.';
      return;
    }

    this.msisdnError = '';
    this.msisdnStats = null;
    this.msisdnSeries = [];
    this.msisdnCharts = [];
    this.msisdnLoading = true;

    const from = this.dateFrom || undefined;
    const to = this.dateTo || undefined;

    this.deviceService.getHzMsisdnStats(msisdn, from, to)
      .pipe(timeout(120000))
      .subscribe({
        next: (data) => {
          this.msisdnStats = data;
          this.msisdnLoading = false;
        },
        error: (err) => {
          console.error('HZ msisdn stats error', err);
          this.msisdnLoading = false;
          this.msisdnError = 'Impossible de charger les statistiques HZ pour ce MSISDN.';
        }
      });

    this.deviceService.getHzMsisdnDailyEvolution(msisdn, from, to)
      .pipe(timeout(120000))
      .subscribe({
        next: (series) => {
          this.msisdnSeries = series;
          this.msisdnCharts = this.buildMsisdnCharts();
          this.applyMsisdnChartTheme();
        },
        error: (err) => {
          console.error('HZ msisdn daily evolution error', err);
        }
      });
  }

  private buildMsisdnCharts(): any[] {
    return this.msisdnSeries.map((s, index) => {
      const color = this.palette[index % this.palette.length];
      return {
        status: s.status,
        label: this.labelFor(s.status),
        data: {
          labels: s.points.map(p => p.date),
          datasets: [{
            label: this.labelFor(s.status),
            data: s.points.map(p => p.devices),
            borderColor: color,
            backgroundColor: this.hexToRgba(color, 0.12),
            fill: true,
            tension: 0.4,
            pointRadius: 2,
            pointHoverRadius: 5,
            pointBackgroundColor: color
          }]
        },
        options: {} as ChartOptions
      };
    });
  }

  private applyMsisdnChartTheme(): void {
    const c = this.chartColors();
    const tooltip = {
      backgroundColor: c.tooltipBg,
      titleColor: c.text,
      bodyColor: c.text2,
      borderColor: c.grid,
      borderWidth: 1
    };

    const options: ChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      color: c.text,
      plugins: { legend: { display: false }, tooltip },
      scales: {
        x: { ticks: { color: c.text2, maxTicksLimit: 10 }, grid: { color: c.grid } },
        y: { ticks: { color: c.text2 }, grid: { color: c.grid } }
      }
    };

    for (const chart of this.msisdnCharts) {
      chart.options = options;
    }
  }

loadOffers(): void {
    this.deviceService.getHzOffers()
      .pipe(timeout(240000))
      .subscribe({
        next: (data) => { this.offers = data; },
        error: (err) => { console.error('HZ offers error', err); }
      });
  }

  loadDashboard(): void {
    this.errorMessage = '';
    this.loading = true;

    const apn = this.selectedOffer === 'all' ? undefined : this.selectedOffer;

    this.deviceService.getHzDailyEvolution(apn)
      .pipe(timeout(180000))
      .subscribe({
        next: (data) => {
          this.series = data;
          this.loading = false;
          this.buildCharts();
          this.applyChartTheme();
        },
        error: (err) => {
          console.error('HZ daily evolution error', err);
          this.loading = false;
          this.errorMessage = 'Impossible de charger l\u2019\u00e9volution quotidienne des erreurs HZ.';
        }
      });
  }

  labelFor(status: string): string {
    return status;
  }

  private buildCharts(): void {
    this.charts = this.series.map((s, index) => {
      const color = this.palette[index % this.palette.length];
      return {
        status: s.status,
        label: this.labelFor(s.status),
        data: {
          labels: s.points.map(p => p.date),
          datasets: [{
            label: this.labelFor(s.status),
            data: s.points.map(p => p.devices),
            borderColor: color,
            backgroundColor: this.hexToRgba(color, 0.12),
            fill: true,
            tension: 0.4,
            pointRadius: 2,
            pointHoverRadius: 5,
            pointBackgroundColor: color
          }]
        },
        options: {} as ChartOptions
      };
    });
  }

  onPointClick(status: string, date: string): void {
    const apn = this.selectedOffer === 'all' ? undefined : this.selectedOffer;
    const params: any = { date, status };
    if (apn) {
      params.apn = apn;
    }
    this.router.navigate(['/hz-errors'], { queryParams: params });
  }

  private hexToRgba(hex: string, alpha: number): string {
    const n = parseInt(hex.replace('#', ''), 16);
    const r = (n >> 16) & 255;
    const g = (n >> 8) & 255;
    const b = n & 255;
    return `rgba(${r},${g},${b},${alpha})`;
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
    const tooltip = {
      backgroundColor: c.tooltipBg,
      titleColor: c.text,
      bodyColor: c.text2,
      borderColor: c.grid,
      borderWidth: 1
    };

    const options: ChartOptions = {
      responsive: true,
      maintainAspectRatio: true,
      color: c.text,
      plugins: { legend: { display: false }, tooltip },
      scales: {
        x: { ticks: { color: c.text2, maxTicksLimit: 10 }, grid: { color: c.grid } },
        y: { ticks: { color: c.text2 }, grid: { color: c.grid } }
      }
    };

    for (const chart of this.charts) {
      const chartStatus = chart.status;
      chart.options = {
        ...options,
        onClick: (evt: any, elements: any[]) => {
          if (elements && elements.length > 0) {
            const index = elements[0].index;
            const date = chart.data.labels[index];
            if (date) {
              this.onPointClick(chartStatus, date);
            }
          }
        }
      };
    }
  }
}

