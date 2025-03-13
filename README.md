# Portfolio Analysis Service

A microservice for real-time portfolio analysis with support for:
- Portfolio overview and metadata
- Time-based analysis across multiple intervals
- Market index integration and benchmarking
- Real-time price updates via Kafka
- Redis caching for performance

## Prerequisites
- Docker
- Docker Compose

## Building and Running

1. Build and start all services:
```bash
docker-compose up --build
```

2. Access the services:
- Portfolio Service: http://localhost:8080
- RedisInsight: http://localhost:8001

## Architecture

The service integrates with:
- Kafka for real-time price updates
- PostgreSQL for persistent storage
- Redis for caching portfolio and market data

## Environment Variables

Key environment variables (configured in docker-compose.yml):
- `SPRING_DATASOURCE_URL`: PostgreSQL connection URL
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Kafka broker address
- `SPRING_DATA_REDISENDPOINT`: Redis connection endpoint

## Time Windows Supported
- 5min, 10min, 15min, 30min
- 1hr
- 1day, 1week, 1month, 1yr
