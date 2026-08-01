export interface Incident {
  requestNumber: string;
  created: string;
  sujet: string;
  msisdn: number;
  offreContrat: string;
}

export interface IncidentWithDeviceInfo {
  requestNumber: string;
  created: string;
  sujet: string;
  msisdn: number;
  offreContrat: string;
  cellName: string;
  rsrp4G: string;
  sinr4G: string;
  rsrp5G: string;
  sinr5G: string;
  cellName5G: string;
  hzError?: string;
  latitude?: number;
  longitude?: number;
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
