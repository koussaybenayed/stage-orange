import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { DeviceListComponent } from './components/device-list/device-list.component';
import { DeviceDetailComponent } from './components/device-detail/device-detail.component';
import { DashboardComponent } from './components/dashboard/dashboard.component';
import { HomeZoneComponent } from './components/home-zone/home-zone.component';
import { HzErrorsComponent } from './components/hz-errors/hz-errors.component';
import { IncidentDevicesComponent } from './components/incident-devices/incident-devices.component';
import { ProblemMapComponent } from './components/problem-map/problem-map.component';

const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: DashboardComponent },
  { path: 'home-zone', component: HomeZoneComponent },
  { path: 'hz-errors', component: HzErrorsComponent },
  { path: 'devices', component: DeviceListComponent },
  { path: 'devices/by-msisdn/:msisdn', component: IncidentDevicesComponent },
  { path: 'devices/:id', component: DeviceDetailComponent },
  { path: 'problem-map', component: ProblemMapComponent },
  { path: '**', redirectTo: '/dashboard' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
