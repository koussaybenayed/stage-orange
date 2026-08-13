import { Component, HostListener } from '@angular/core';
import { Observable } from 'rxjs';
import { ThemeMode, ThemeService } from './services/theme.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'Orange Monitoring';

  sidebarCollapsed = false;
  sidebarOpen = false;

  theme$: Observable<ThemeMode>;

  private hovered = false;
  private isMobile = false;

  constructor(private themeService: ThemeService) {
    this.theme$ = this.themeService.theme$;
  }

  ngOnInit(): void {
    this.isMobile = window.innerWidth < 992;
    this.sidebarCollapsed = localStorage.getItem('orange-monitoring-sidebar') === 'collapsed';
  }

  get isCollapsedDisplay(): boolean {
    return !this.isMobile && this.sidebarCollapsed && !this.hovered;
  }

  @HostListener('window:resize', ['$event'])
  onResize(): void {
    const wasMobile = this.isMobile;
    this.isMobile = window.innerWidth < 992;
    if (!this.isMobile) {
      this.sidebarOpen = false;
    }
    if (wasMobile && !this.isMobile) {
      this.hovered = false;
    }
  }

  toggleCollapse(): void {
    this.sidebarCollapsed = !this.sidebarCollapsed;
    this.hovered = false;
    localStorage.setItem('orange-monitoring-sidebar', this.sidebarCollapsed ? 'collapsed' : 'expanded');
  }

  toggleMobileSidebar(): void {
    this.sidebarOpen = !this.sidebarOpen;
  }

  closeMobileSidebar(): void {
    this.sidebarOpen = false;
  }

  onSidebarEnter(): void {
    this.hovered = true;
  }

  onSidebarLeave(): void {
    this.hovered = false;
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }
}
