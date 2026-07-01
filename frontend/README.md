# Device Monitoring Frontend

Angular frontend for the device monitoring system. Displays real-time network device data with charts and analytics.

## Features

- Device list with pagination and search
- Detailed device view with edit capabilities
- Dashboard with charts and statistics
- CRUD operations for devices
- Responsive design

## Prerequisites

- Node.js 16+ and npm 7+
- Angular CLI 17+

## Installation

1. **Install Dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Configure API URL**
   - Edit `src/environments/environment.ts`
   - Update `apiUrl` if your backend is running on a different port

3. **Run Development Server**
   ```bash
   npm start
   ```

   Navigate to `http://localhost:4200/`

## Build for Production

```bash
npm run build:prod
```

The build artifacts will be stored in the `dist/` directory.

## Project Structure

```
src/
├── app/
│   ├── components/
│   │   ├── device-list/       # List view of all devices
│   │   ├── device-detail/     # Individual device details
│   │   └── dashboard/         # Dashboard with charts
│   ├── services/
│   │   └── device.service.ts  # API communication
│   ├── models/
│   │   └── device.model.ts    # TypeScript interfaces
│   ├── app.component.*        # Root component
│   ├── app.module.ts          # App module
│   └── app-routing.module.ts  # Routing configuration
├── assets/                    # Static assets
├── environments/              # Environment configs
├── index.html                 # Main HTML file
├── main.ts                    # Bootstrap file
└── styles.css                 # Global styles
```

## Routes

- `/` - Redirects to Dashboard
- `/dashboard` - Dashboard with charts and statistics
- `/devices` - List of all devices with search/pagination
- `/devices/:id` - Detailed view of a specific device

## Available npm Scripts

- `npm start` - Runs the dev server
- `npm run build` - Builds for development
- `npm run build:prod` - Builds for production
- `npm run watch` - Watches for file changes
- `npm test` - Runs unit tests
- `npm run lint` - Runs linter

## API Integration

The app communicates with the Spring Boot backend via HTTP requests. Ensure the backend is running on `http://localhost:8080`.

### API Endpoints Used

- `GET /api/devices` - Get paginated list
- `GET /api/devices/search` - Search devices
- `GET /api/devices/:id` - Get device details
- `POST /api/devices` - Create device
- `PUT /api/devices/:id` - Update device
- `DELETE /api/devices/:id` - Delete device
- `GET /api/devices/stats/total` - Get total count
- `GET /api/devices/all` - Get all devices

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)
