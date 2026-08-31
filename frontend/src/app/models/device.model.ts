export interface Incident {
  requestNumber: string;
  created: string;
  sujet: string;
  msisdn: number;
  offreContrat: string;
  contact?: string;
}

export interface IncidentWithDeviceInfo {
  requestNumber: string;
  created: string;
  sujet: string;
  description?: string;
  contact?: string;
  msisdn: number;
  offreContrat: string;
  productClass: string;
  cellName: string;
  rsrp4G: string;
  sinr4G: string;
  rsrp5G: string;
  sinr5G: string;
  cellName5G: string;
  hzError?: string;
  debugImsi?: number;
  latitude?: number;
  longitude?: number;
  x?: number;
  y?: number;
  congestionnee?: boolean;
  action?: string;
  siteCode?: string;
  hasIncident?: boolean;
  incidentPeriod?: string;
  incidentTech?: string;
}

export interface NameCount {
  name: string;
  count: number;
}

export interface IncidentOverview {
  totalIncidents: number;
  lastDay: number;
  last7Days: number;
}

export interface TopZonesResponse {
  zones: NameCount[];
  totalSites: number;
}

export interface HzDailyPoint {
  date: string;
  devices: number;
}

export interface HzDailySeries {
  status: string;
  points: HzDailyPoint[];
}

export interface HzMsisdnStats {
  msisdn: number;
  totalErrors: number;
  byStatus: NameCount[];
  recentErrors: HzError[];
}

export interface NearbySite {
  site: string;
  latitude: number;
  longitude: number;
  hasIncident: boolean;
  incidentPeriod?: string;
  incidentTech?: string;
}

export interface HzError {
  time: string;
  msisdn: number;
  imsi?: number;
  siteName?: string;
  errorCode?: number;
  status: string;
  apn?: string;
  count: number;
  rsrp4G?: string;
  sinr4G?: string;
  rsrp5G?: string;
  sinr5G?: string;
  cellName?: string;
  cellName5G?: string;
  congestionnee?: boolean;
  hasIncident?: boolean;
  incidentPeriod?: string;
  siteCode?: string;
}

export interface AcsMaxBox5G {
  id?: number;
  serialNumber: string;
  msisdn?: number;
  imei?: number;
  ip: string;
  lastInform?: string;
  registered?: string;
  version: string;
  sinr?: string;
  sinr5G?: string;
  rsrp?: string;
  rsrp5G?: string;
  rsrq?: string;
  rsrq5G?: string;
  imsi?: number;
  cellId?: string;
  pci?: number;
  pci5G?: number;
  downlinkThroughput?: string;
  uplinkThroughput?: string;
  ipData?: string;
  lastBoot?: string;
  apnData?: string;
  cellName?: string;
  latitude?: number;
  longitude?: number;
}

export interface PageResponse<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: any;
    paged: boolean;
    unpaged: boolean;
  };
  last: boolean;
  totalPages: number;
  totalElements: number;
  first: boolean;
  size: number;
  number: number;
  numberOfElements: number;
  empty: boolean;
}
