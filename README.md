# Simple Library Management System

A Spring Boot application that provides a RESTful API to manage books and their copies in a simple library system.

## Docker assignment

See [DOCKER-ZADANIE.md](DOCKER-ZADANIE.md) for the Slovak walkthrough: container build,
API login, PostgreSQL volume persistence, multiple instances, Docker Hub publication, and presentation questions.

```powershell
docker compose up -d --build --scale app=2 --wait --wait-timeout 180
docker compose ps
docker compose port nginx 80
```

Use **http://localhost:8080** for the Docker API, including `POST /auth/login` and
`GET /api/books`. Nginx distributes requests across the two application instances.
The app containers expose port 8081 only inside Docker. Set `NGINX_PORT` in `.env`
to change the host port. The `X-Upstream-Addr` response header identifies the backend
that handled each request; `docker compose logs -f nginx` also shows routing.

Containers use PostgreSQL; normal IDE runs use the in-memory H2 database. Redis caching is disabled by default.

## Features

- Add, retrieve, update, and delete books
- Manage multiple copies of each book
- Check availability of book copies
- RESTful API with proper request validation and error handling
- Swagger (OpenAPI) documentation for API endpoints
- Unit tests for services and controllers

## Tech Stack

- Java
- Spring Boot
- Spring Data JPA
- H2 Database (for development/testing)
- Swagger (springdoc-openapi)
- JUnit & Mockito (for testing)

## Getting Started

### Prerequisites

- Java 17 or later
- Maven

### Clone the repository

```bash
git clone https://github.com/your-username/library-management-system.git
cd library-management-system
```

### Run the application

```bash
./mvnw spring-boot:run
```

The app will start at: `http://localhost:8081`

Read books at `GET /api/books`. To obtain a JWT, send `POST /auth/login` with
`{"username":"Viktor","password":"pass"}` as JSON. Use the returned token as a Bearer token for write requests.
The login currently checks only the hardcoded password, not a database account.

### Swagger UI

Swagger routes are protected by the current security configuration. They are not a public login UI.
The configured paths are:

```
http://localhost:8081/swagger-ui.html
```

or

```
http://localhost:8081/swagger-ui/index.html
```

To explore and test the API.

## API Endpoints

| Method | Endpoint                        | Description                         |
|--------|----------------------------------|-------------------------------------|
| GET    | `/api/books`                    | Get all books                       |
| POST   | `/api/books`                    | Add a new book                      |
| GET    | `/api/books/{id}`              | Get a book by ID (including copies) |
| PUT    | `/api/books/{id}`              | Update a book by ID                 |
| DELETE | `/api/books/{id}`              | Delete a book by ID                 |
| GET    | `/api/books/{id}/copies`       | Get copies of a book                |
| POST   | `/api/books/{id}/copies`       | Add a new copy for a book           |
| PUT    | `/api/books/{id}/copies/{cid}` | Update availability of a copy       |

## Example JSON Payloads

### Add a Book

```json
{
  "title": "Clean Code",
  "author": "Robert C. Martin",
  "isbn": "978-0132350884",
  "publishedYear": 2008
}
```

### Update a Book

```json
{
  "title": "Clean Code (2nd Edition)",
  "publishedYear": 1999
}
```

### Update Book Copy Availability

```json
{
  "available": false
}
```

## Testing

To run tests:

```bash
./mvnw test
```

## H2 Database Console

The H2 console is not enabled by default. IDE runs use an in-memory database whose data is lost on shutdown.
Docker runs use PostgreSQL and a persistent volume instead.

**JDBC URL:** `jdbc:h2:mem:testdb`  
**User:** `sa`  
**Password:** `password`

## License

This project is open-source and available under the [MIT License](LICENSE).
