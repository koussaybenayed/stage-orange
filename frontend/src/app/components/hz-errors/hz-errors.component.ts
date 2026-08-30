import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { timeout } from 'rxjs/operators';
import { DeviceService } from '../../services/device.service';
import { HzError, NameCount } from '../../models/device.model';

@Component({
  selector: 'app-hz-errors',
  templateUrl: './hz-errors.component.html',
  styleUrls: ['./hz-errors.component.css']
})
export class HzErrorsComponent implements OnInit, OnDestroy {
  date: string = '';
  status: string = '';
  apn: string = '';
  errors: HzError[] = [];
  offers: NameCount[] = [];

  isLoading = true;
  errorMessage = '';

  pageSize = 25;
  currentPage = 0;

  skeletonRows = Array(8).fill(0);
  skeletonCols = Array(7).fill(0);

  private querySub: Subscription | undefined;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private deviceService: DeviceService
  ) { }

  ngOnInit(): void {
    this.querySub = this.route.queryParams.subscribe(params => {
      this.date = params['date'] || '';
      this.status = params['status'] || '';
      this.apn = params['apn'] || '';
      if (this.date && this.status) {
        this.loadErrors();
      }
    });
    this.loadOffers();
  }

  ngOnDestroy(): void {
    this.querySub?.unsubscribe();
  }

  loadOffers(): void {
    this.deviceService.getHzOffers()
      .pipe(timeout(240000))
      .subscribe({
        next: (data) => { this.offers = data; },
        error: (err) => { console.error('HZ offers error', err); }
      });
  }

  loadErrors(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.currentPage = 0;

    this.deviceService.getHzErrors(this.date, this.status, this.apn, 200)
      .pipe(timeout(180000))
      .subscribe({
        next: (data) => {
          this.isLoading = false;
          this.errors = data;
        },
        error: (err) => {
          console.error('HZ errors load error', err);
          this.isLoading = false;
          this.errorMessage = 'Impossible de charger la liste des erreurs HZ.';
        }
      });
  }

  onOfferChange(): void {
    const apn = this.apn === 'all' || this.apn === '' ? '' : this.apn;
    this.router.navigate(['/hz-errors'], {
      queryParams: { date: this.date, status: this.status, apn }
    });
  }

  get pagedErrors(): HzError[] {
    const start = this.currentPage * this.pageSize;
    return this.errors.slice(start, start + this.pageSize);
  }

  get totalPages(): number {
    return Math.ceil(this.errors.length / this.pageSize);
  }

  prevPage(): void {
    if (this.currentPage > 0) this.currentPage--;
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.errors.length) this.currentPage++;
  }

  goBack(): void {
    this.router.navigate(['/home-zone']);
  }
}
