import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { timeout } from 'rxjs/operators';
import { DeviceService } from '../../services/device.service';
import { IncidentWithDeviceInfo } from '../../models/device.model';
import { hzErrorTranslation } from '../../models/hz-error-translations';

@Component({
  selector: 'app-device-list',
  templateUrl: './device-list.component.html',
  styleUrls: ['./device-list.component.css']
})
export class DeviceListComponent implements OnInit {
  incidents: IncidentWithDeviceInfo[] = [];
  filteredIncidents: IncidentWithDeviceInfo[] = [];
  pagedIncidents: IncidentWithDeviceInfo[] = [];
  isLoading = false;
  errorMessage = '';

  filterHzError = '';
  filterSujet = '';
  filterOffre = '';
  filterCongestion = '';

  pageSize = 20;
  currentPage = 0;
  offerContrats: string[] = [];
  hzErrorTypes: string[] = [];
  sujets: string[] = [];

  skeletonRows = Array(8).fill(0);
  skeletonCols = Array(15).fill(0);

  constructor(private deviceService: DeviceService, private router: Router) { }

  ngOnInit(): void {
    this.loadIncidents();
  }

  loadIncidents(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.deviceService.getIncidentsWithDeviceInfo()
      .pipe(timeout(60000))
      .subscribe({
        next: (data) => {
          this.isLoading = false;
          try {
            this.incidents = data;
            this.loadSujets();
            this.loadOfferContrats();
            this.loadHzErrorTypes();
            this.applyFilters();
          } catch (err) {
            console.error('Error processing incidents', err);
            this.errorMessage = 'Erreur lors du traitement des incidents.';
          }
        },
        error: (error) => {
          console.error('Error loading incidents', error);
          this.errorMessage = 'Impossible de charger les incidents. Vérifiez que le backend est démarré.';
          this.isLoading = false;
        }
      });
  }

  private loadSujets(): void {
    const set = new Set<string>();
    for (const inc of this.incidents) {
      if (inc.sujet) {
        set.add(inc.sujet);
      }
    }
    this.sujets = Array.from(set).sort((a, b) => a.localeCompare(b));
  }

  private loadOfferContrats(): void {
    const set = new Set<string>();
    for (const inc of this.incidents) {
      if (inc.offreContrat) {
        set.add(inc.offreContrat);
      }
    }
    this.offerContrats = Array.from(set).sort();
  }

  private loadHzErrorTypes(): void {
    const set = new Set<string>();
    for (const inc of this.incidents) {
      for (const status of this.hzErrorStatuses(inc)) {
        set.add(status);
      }
    }
    this.hzErrorTypes = Array.from(set).sort((a, b) => a.localeCompare(b));
  }

  private hzErrorStatuses(inc: IncidentWithDeviceInfo): string[] {
    const hz = inc.hzError;
    if (!hz) return [];
    const out: string[] = [];
    for (const part of hz.split(',')) {
      const p = part.trim();
      if (!p) continue;
      const m = p.match(/^(.*?)\((\d+)\)$/);
      out.push(m ? m[1].trim() : p);
    }
    return out;
  }

  onChangeSujet(): void {
    this.applyFilters();
  }

  onChangeHzError(): void {
    this.applyFilters();
  }

  onChangeOffre(): void {
    this.applyFilters();
  }

  onChangeCongestion(): void {
    this.applyFilters();
  }

  applyFilters(): void {
    const hz = this.filterHzError?.trim() || '';
    this.filteredIncidents = this.incidents.filter(inc => {
      const hzMatch = !hz || this.hzErrorStatuses(inc).some(s => s === hz);
      const sujetMatch = !this.filterSujet || inc.sujet === this.filterSujet;
      const offreMatch = !this.filterOffre || inc.offreContrat === this.filterOffre;
      const congMatch = !this.filterCongestion ||
        (this.filterCongestion === 'true' && !!inc.congestionnee) ||
        (this.filterCongestion === 'false' && !inc.congestionnee);
      return hzMatch && sujetMatch && offreMatch && congMatch;
    });
    this.currentPage = 0;
    this.applyPaging();
  }

  applyPaging(): void {
    const start = this.currentPage * this.pageSize;
    this.pagedIncidents = this.filteredIncidents.slice(start, start + this.pageSize);
  }

  nextPage(): void {
    if ((this.currentPage + 1) * this.pageSize < this.filteredIncidents.length) {
      this.currentPage++;
      this.applyPaging();
    }
  }

  prevPage(): void {
    if (this.currentPage > 0) {
      this.currentPage--;
      this.applyPaging();
    }
  }

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.filteredIncidents.length / this.pageSize));
  }

  async exportToExcel(): Promise<void> {
    if (this.filteredIncidents.length === 0) return;

    const XLSX = await import('xlsx');

    const buildRow = (inc: IncidentWithDeviceInfo) => ({
      'Numéro de demande': inc.requestNumber,
      'Date de création': inc.created,
      'Sujet': inc.sujet,
      'MSISDN': inc.msisdn,
      'IMSI concerné': inc.debugImsi ?? '-',
      'Offre/Contrat': inc.offreContrat,
      'Cell Name 4G': inc.cellName || '-',
      'Cell Name 5G': inc.cellName5G || '-',
      'Congestionnée': inc.congestionnee ? 'Oui' : 'Non',
      'Action': inc.action || '-',
      'RSRP 4G': inc.rsrp4G || '-',
      'SINR 4G': inc.sinr4G || '-',
      'RSRP 5G': inc.rsrp5G || '-',
      'SINR 5G': inc.sinr5G || '-',
      'HZerror': inc.hzError || '-',
      'Latitude': inc.latitude ?? '',
      'Longitude': inc.longitude ?? ''
    });

    const smc = this.filteredIncidents.filter(inc => !!inc.description);
    const intervention = this.filteredIncidents.filter(inc => !inc.description);

    const wb = XLSX.utils.book_new();
    const appendSheet = (name: string, data: IncidentWithDeviceInfo[]) => {
      const ws = XLSX.utils.json_to_sheet(data.map(buildRow));
      ws['!cols'] = [
        { wch: 20 }, { wch: 20 }, { wch: 32 }, { wch: 14 },
        { wch: 18 }, { wch: 24 }, { wch: 30 }, { wch: 26 },
        { wch: 14 }, { wch: 20 }, { wch: 10 }, { wch: 10 },
        { wch: 10 }, { wch: 10 }, { wch: 42 }, { wch: 12 },
        { wch: 12 }
      ];
      XLSX.utils.book_append_sheet(wb, ws, name);
    };

    if (smc.length > 0) {
      appendSheet('Pour SMC', smc);
    }
    if (intervention.length > 0) {
      appendSheet('Pour Intervention', intervention);
    }
    if (wb.SheetNames.length === 0) return;

    const stamp = new Date().toISOString().slice(0, 16).replace(/[T:]/g, '-');
    XLSX.writeFile(wb, `incidents_${stamp}.xlsx`);
  }

  clearFilters(): void {
    this.filterHzError = '';
    this.filterSujet = '';
    this.filterOffre = '';
    this.filterCongestion = '';
    this.applyFilters();
  }

  viewDevices(msisdn: number): void {
    this.router.navigate(['/devices/by-msisdn', msisdn]);
  }

  hasLocation(inc: IncidentWithDeviceInfo): boolean {
    return !!inc.latitude && !!inc.longitude;
  }

  viewOnMap(inc: IncidentWithDeviceInfo): void {
    if (!this.hasLocation(inc)) return;
    this.router.navigate(['/problem-map'], {
      queryParams: {
        lat: inc.latitude,
        lng: inc.longitude,
        incident: inc.requestNumber,
        site: this.siteNameOf(inc)
      }
    });
  }

  hzErrorTooltip(inc: IncidentWithDeviceInfo): string {
    if (!inc.hzError || inc.hzError === 'No HZ errors') {
      return '';
    }
    return inc.hzError.split(',').map(p => {
      const m = p.trim().match(/^(.*?)\((\d+)\)$/);
      const raw = m ? m[1].trim() : p.trim();
      const count = m ? ` (${m[2]})` : '';
      const expl = hzErrorTranslation(raw);
      return expl ? `${raw}${count} — ${expl}` : p.trim();
    }).join(', ');
  }

  private siteNameOf(inc: IncidentWithDeviceInfo): string {
    return inc.cellName && inc.cellName.length >= 8
      ? inc.cellName.substring(0, 8)
      : 'Site inconnu';
  }
}
