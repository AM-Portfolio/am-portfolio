Overview of the Codebase
==========================
The codebase is a Java-based portfolio application that utilizes various technologies such as Redis, Kafka, and MongoDB. The application is designed to manage and analyze portfolio data, including stock prices, market indices, and portfolio holdings. The codebase is divided into two main modules: `portfolio-redis` and `portfolio-app`.

### Key Components and their Purposes

*   **RedisConfig**: This is a configuration class that sets up the Redis connection and caching mechanism for the application. It defines the Redis host, password, and cache time-to-live (TTL) settings.
*   **application.yml**: This is a configuration file that contains settings for the application, including MongoDB, Kafka, and Redis connections. It also defines various properties such as topic names, consumer IDs, and security settings.
*   **RedisConnectionFactory**: This is a bean that creates a Redis connection factory, which is used to establish connections to the Redis server.
*   **RedisTemplate**: This is a bean that creates a Redis template, which is used to perform operations on the Redis cache.
*   **AnalyticsModuleConfig**: This is a configuration class for the portfolio-analytics module. It ensures that Spring can discover and load all components in this module.
*   **AbstractAnalyticsProvider**: This is a common base class for all analytics providers (index and portfolio). It provides methods for generating analytics and fetching market data.
*   **AnalyticsFactory**: This is a factory for creating and managing analytics providers for both index and portfolio analytics.

### API Documentation

The codebase does not provide a RESTful API for authentication. However, it does provide a configuration class `RedisConfig` that sets up the Redis connection and caching mechanism.

#### RedisConfig API

*   **redisConnectionFactory()**: This method creates a Redis connection factory, which is used to establish connections to the Redis server.
*   **redisTemplate()**: This method creates a Redis template, which is used to perform operations on the Redis cache.

### Usage Examples

To use the Redis configuration in the application, you can inject the `RedisTemplate` bean into your service classes and use it to perform operations on the Redis cache.

```java
@Service
public class PortfolioService {
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    // Use the redisTemplate to perform operations on the Redis cache
}
```

### Architecture Notes

The codebase follows a modular architecture, with separate modules for portfolio analytics and Redis configuration. The analytics module uses a factory pattern to support extensibility for future analytics types. The Redis configuration module uses a configuration class to set up the Redis connection and caching mechanism.

The codebase also uses Spring Framework for dependency injection and configuration management. It uses MongoDB, Kafka, and Redis for data storage and messaging.

### Helm Chart

The codebase includes a Helm chart for deploying the Market Data Service to AKS (Azure Kubernetes Service). The chart provides automated deployment of the Market Data Service with optimized configurations, built-in retry mechanism, comprehensive metrics collection, and Grafana dashboards for monitoring.

### Installation

To install the Helm chart, you need to add the required Helm repositories, create a values override file, and install the chart using the `helm install` command.

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add influxdata https://helm.influxdata.com/
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm install market-data ./market-data -f custom-values.yaml -n market-data --create-namespace
```

### Configuration

The Helm chart provides various configuration options, including market data processing, resource allocation, and autoscaling. You can customize these settings by creating a values override file and passing it to the `helm install` command.

```yaml
config:
  marketData:
    maxRetries: 3              # Maximum retry attempts for API calls
    retryDelayMs: 1000        # Base delay between retries
    threadPoolSize: 5         # Thread pool size for parallel processing
    threadQueueCapacity: 10   # Queue capacity for pending tasks
    maxAgeMinutes: 15        # Maximum age of market data
```