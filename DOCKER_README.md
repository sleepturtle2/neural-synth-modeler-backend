# Docker Setup for Neural Synth Java Backend

This document provides instructions for building and running the Spring Boot backend service using Docker.

## Prerequisites

- Docker installed on your system
- Docker Compose (usually comes with Docker Desktop)
- At least 4GB of available RAM for the entire stack

## Quick Start with Docker Compose

The easiest way to run the entire stack is using Docker Compose:

```bash
# Build and start all services
docker-compose up --build

# Run in detached mode
docker-compose up -d --build

# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

## Manual Docker Build

If you prefer to build and run the backend service manually:

### 1. Build the Docker Image

```bash
# Build the image
docker build -t neural-synth-backend:latest .

# Build with a specific tag
docker build -t neural-synth-backend:v1.0.0 .
```

### 2. Run the Container

```bash
# Run with default configuration
docker run -p 8080:8080 neural-synth-backend:latest

# Run with custom environment variables
docker run -p 8080:8080 \
  -e MYSQL_URL=jdbc:mysql://your-mysql-host:3306/NEURAL_SYNTH \
  -e MONGODB_URI=mongodb://your-mongo-host:27017/neural_synth \
  -e MODEL_SERVER_URL=http://your-bentoml-host:3000 \
  neural-synth-backend:latest
```

## Environment Variables

The following environment variables can be configured:

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_URL` | `jdbc:mysql://localhost:3306/NEURAL_SYNTH` | MySQL connection URL |
| `MYSQL_USERNAME` | `readwrite` | MySQL username |
| `MYSQL_PASSWORD` | `readwrite` | MySQL password |
| `MONGODB_URI` | `mongodb://localhost:27017/neural_synth` | MongoDB connection URI |
| `MONGODB_DATABASE` | `neural_synth` | MongoDB database name |
| `MODEL_SERVER_URL` | `http://localhost:3000` | BentoML model service URL |

## Health Check

The container includes a health check that verifies the application is running:

```bash
# Check container health
docker ps

# View health check logs
docker inspect neural-synth-backend
```

## Troubleshooting

### Common Issues

1. **Port already in use**: Make sure port 8080 is not being used by another service
2. **Database connection issues**: Ensure MySQL and MongoDB are running and accessible
3. **Model service not found**: Verify the BentoML service is running on the specified URL

### Logs

```bash
# View application logs
docker logs neural-synth-backend

# Follow logs in real-time
docker logs -f neural-synth-backend
```

### Container Shell Access

```bash
# Access container shell
docker exec -it neural-synth-backend /bin/sh
```

## Development

For development, you can mount the source code as a volume:

```bash
docker run -p 8080:8080 \
  -v $(pwd)/src:/app/src \
  -v $(pwd)/target:/app/target \
  neural-synth-backend:latest
```

## Production Considerations

1. **Security**: Change default passwords in production
2. **Resource Limits**: Set appropriate memory and CPU limits
3. **Logging**: Configure external logging solutions
4. **Monitoring**: Add monitoring and alerting
5. **Backup**: Set up database backup strategies

## API Endpoints

Once running, the following endpoints will be available:

- `GET /v1` - Server metadata
- `GET /v1/health/live` - Health check
- `GET /v1/health/ready` - Readiness check
- `GET /v1/models/{modelName}` - Model metadata
- `POST /v1/models/{modelName}/infer` - Inference endpoint
- `GET /v1/infer-audio/status/{id}` - Status check
- `GET /v1/preset/{id}` - Download preset 