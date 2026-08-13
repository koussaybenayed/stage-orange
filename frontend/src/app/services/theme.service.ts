import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

export type ThemeMode = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly STORAGE_KEY = 'orange-monitoring-theme';

  private themeSubject = new BehaviorSubject<ThemeMode>(this.getInitialTheme());
  theme$: Observable<ThemeMode> = this.themeSubject.asObservable();

  get theme(): ThemeMode {
    return this.themeSubject.value;
  }

  constructor() {
    this.apply(this.themeSubject.value);
  }

  toggle(): void {
    const next: ThemeMode = this.theme === 'dark' ? 'light' : 'dark';
    this.themeSubject.next(next);
    localStorage.setItem(this.STORAGE_KEY, next);
    this.apply(next);
  }

  setTheme(theme: ThemeMode): void {
    this.themeSubject.next(theme);
    localStorage.setItem(this.STORAGE_KEY, theme);
    this.apply(theme);
  }

  private getInitialTheme(): ThemeMode {
    const stored = localStorage.getItem(this.STORAGE_KEY);
    if (stored === 'dark' || stored === 'light') {
      return stored;
    }
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  }

  private apply(theme: ThemeMode): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}
