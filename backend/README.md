# Device Monitoring Backend

Spring Boot REST API for network device monitoring system.

## Features

- CRUD operations for ACS MaxBox 5G devices
- Search functionality with pagination and sorting
- RESTful API endpoints
- MySQL database integration
- CORS support for Angular frontend

## Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0+

## Setup Instructions

1. **Database Setup**
   - Ensure MySQL is running
   - Import the `orange_db.sql` file into your MySQL server
   - Update `application.properties` with your MySQL credentials if needed

2. **Build the Application**
   ```bash
   cd backend
   mvn clean install
   ```

3. **Run the Application**
   ```bash
   mvn spring-boot:run
   ```

   The API will be available at: `http://localhost:8080`

## API Endpoints

### Devices
- `GET /api/devices` - Get all devices (paginated)
- `GET /api/devices/{id}` - Get device by ID
- `GET /api/devices/search?searchTerm=value` - Search devices
- `POST /api/devices` - Create new device
- `PUT /api/devices/{id}` - Update device
- `DELETE /api/devices/{id}` - Delete device
- `GET /api/devices/stats/total` - Get total device count
- `GET /api/devices/all` - Get all devices (unpaged)

## Query Parameters

- `page` - Page number (0-indexed)
- `size` - Page size (default: 10)
- `sort` - Sort field (e.g., `lastInform,desc`)

## Example Requests

```bash
# Get first page of devices
curl http://localhost:8080/api/devices?page=0&size=10&sort=lastInform,desc

# Search for devices
curl http://localhost:8080/api/devices/search?searchTerm=192.238&page=0&size=10

# Get specific device
curl http://localhost:8080/api/devices/1

# Create new device
curl -X POST http://localhost:8080/api/devices \
  -H "Content-Type: application/json" \
  -d '{"serialNumber":"TEST123","ip":"192.168.1.1","version":"1.0.0"}'
```

## Configuration

Edit `src/main/resources/application.properties` to modify:
- Database connection details
- Server port
- JPA/Hibernate settings
- Logging levels
