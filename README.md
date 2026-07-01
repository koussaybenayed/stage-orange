# Device Monitoring System

Complete Spring Boot + Angular application for monitoring network devices from the Orange database.

## Project Structure

```
stage-orange/
├── backend/                    # Spring Boot REST API
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/orange/monitoring/
│   │   │   │       ├── controller/
│   │   │   │       ├── entity/
│   │   │   │       ├── repository/
│   │   │   │       ├── service/
│   │   │   │       └── DeviceMonitoringApplication.java
│   │   │   └── resources/
│   │   │       └── application.properties
│   ├── pom.xml
│   └── README.md
│
├── frontend/                   # Angular Application
│   ├── src/
│   │   ├── app/
│   │   │   ├── components/
│   │   │   │   ├── device-list/
│   │   │   │   ├── device-detail/
│   │   │   │   └── dashboard/
│   │   │   ├── services/
│   │   │   ├── models/
│   │   │   ├── app.module.ts
│   │   │   └── app-routing.module.ts
│   │   ├── environments/
│   │   ├── index.html
│   │   └── main.ts
│   ├── package.json
│   ├── angular.json
│   ├── tsconfig.json
│   └── README.md
│
├── orange_db.sql              # Database dump
└── README.md                  # This file
```

## Quick Start

### Backend Setup

1. **Update Database Connection**
   - Edit `backend/src/main/resources/application.properties`
   - Update MySQL credentials if needed

2. **Build and Run**
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```

   Backend will run on `http://localhost:8080`

### Frontend Setup

1. **Install Dependencies**
   ```bash
   cd frontend
   npm install
   ```

2. **Run Development Server**
   ```bash
   npm start
   ```

   Frontend will run on `http://localhost:4200`

## Features

### Backend
- RESTful API with CRUD operations
- Advanced search with pagination and sorting
- MySQL database integration with JPA/Hibernate
- CORS support for frontend integration
- Comprehensive error handling

### Frontend
- **Device List**: Browse all devices with pagination
- **Search**: Filter devices by serial number, IP, version, or cell ID
- **Device Details**: View and edit individual device information
- **Dashboard**: Real-time charts and statistics
  - Total devices count
  - Online/offline status
  - Signal quality metrics (SINR, RSRP, RSRQ)
  - Throughput analysis
  - Device registration status

## Technology Stack

### Backend
- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- Hibernate ORM
- MySQL 8.0+
- Maven

### Frontend
- Angular 17
- TypeScript 5.2
- Chart.js (ng2-charts)
- RxJS
- Bootstrap styling

## API Endpoints

### Device Management
```
GET    /api/devices                    - Get all devices (paginated)
GET    /api/devices/{id}               - Get device by ID
GET    /api/devices/search?searchTerm  - Search devices
POST   /api/devices                    - Create device
PUT    /api/devices/{id}               - Update device
DELETE /api/devices/{id}               - Delete device
GET    /api/devices/stats/total        - Get total device count
GET    /api/devices/all                - Get all devices
```

## Query Parameters

- `page` - Page number (default: 0)
- `size` - Page size (default: 10)
- `sort` - Sort field and direction (default: `lastInform,desc`)

## Example Requests

```bash
# Get devices on page 2
curl http://localhost:8080/api/devices?page=1&size=10

# Search for devices
curl http://localhost:8080/api/devices/search?searchTerm=192.238

# Sort by version
curl http://localhost:8080/api/devices?sort=version,asc
```

## Development Workflow

1. Start the backend server
2. Start the Angular dev server
3. Open `http://localhost:4200` in your browser
4. Make changes and they'll auto-reload

## Building for Production

### Backend
```bash
cd backend
mvn clean package -DskipTests
java -jar target/device-monitoring-backend-1.0.0.jar
```

### Frontend
```bash
cd frontend
npm run build:prod
# Serve the dist/ folder with a web server
```

## Troubleshooting

### CORS Errors
- Ensure backend is running on `http://localhost:8080`
- Check `application.properties` CORS configuration

### Database Connection Issues
- Verify MySQL is running
- Check credentials in `application.properties`
- Ensure `orange_db` database exists

### Angular Build Issues
- Clear node_modules: `rm -rf node_modules && npm install`
- Clear Angular cache: `ng cache clean`

## Contributing

1. Create feature branches
2. Make changes with descriptive commits
3. Test thoroughly
4. Submit pull requests

## License

This project is for educational purposes.
