# AM-Portfolio Codebase Documentation

## Table of Contents
- [1. Overview](#1-overview)
- [2. Key Components and Their Purposes](#2-key-components-and-their-purposes)
- [3. Authentication Module Documentation](#3-authentication-module-documentation)
  - [3.1 Overview](#31-overview)
  - [3.2 Components](#32-components)
  - [3.3 Configuration](#33-configuration)
  - [3.4 Usage](#34-usage)
- [4. API Documentation](#4-api-documentation)
- [5. Usage Examples](#5-usage-examples)
- [6. Architecture Notes](#6-architecture-notes)

---

## 1. Overview

The **AM-Portfolio** project is a microservice-based portfolio management system designed to handle financial portfolio data, market indices, stock prices, and related analytics. It leverages modern technologies such as Spring Boot, Redis caching, Kafka messaging, and MongoDB for persistence. The system is designed for scalability, real-time data processing, and efficient caching to deliver responsive portfolio insights.

---

## 2. Key Components and Their Purposes

| Component                         | Purpose                                                                                       |
|----------------------------------|-----------------------------------------------------------------------------------------------|
| **portfolio-redis**               | Redis caching configuration and templates to cache portfolio data, market indices, and stock prices. |
| **portfolio-app**                 | Main Spring Boot application handling business logic, Kafka consumers, REST APIs, and integration with Redis and MongoDB. |
| **Kafka Integration**             | Provides real-time streaming of portfolio updates, stock price updates, and market index changes. |
| **MongoDB Persistence**           | Stores portfolio data and historical records for durability and querying.                      |
| **Redis Caching Layer**           | Caches frequently accessed data such as portfolio summaries, holdings, and market indices to improve performance. |
| **Authentication Module**         | Manages user authentication, authorization, and security for accessing portfolio services. (Focus of this documentation) |

---

## 3. Authentication Module Documentation

### 3.1 Overview

The **Authentication Module** in AM-Portfolio handles user authentication and authorization to secure access to portfolio data and APIs. It integrates with Spring Security and supports token-based authentication mechanisms (e.g., JWT), ensuring that only authorized users can access sensitive portfolio information.

This documentation covers the module's configuration, components, and usage patterns.

---

### 3.2 Components

| Component                          | Description                                                                                      |
|----------------------------------|------------------------------------------------------------------------------------------------|
| **Security Configuration**        | Defines security filters, authentication providers, and access rules for HTTP endpoints.        |
| **Authentication Manager**        | Handles authentication logic, including validating credentials and issuing tokens.              |
| **UserDetailsService Implementation** | Loads user-specific data during authentication from a user store (e.g., database or LDAP).     |
| **JWT Token Provider (if applicable)** | Generates and validates JWT tokens for stateless authentication.                               |
| **Authentication Controllers**    | REST controllers exposing login, logout, and token refresh endpoints.                            |
| **Exception Handlers**             | Handles authentication and authorization exceptions gracefully.                                 |

---

### 3.3 Configuration

Authentication module configurations are typically defined in:

- **Spring Security Config class**: Configures HTTP security, authentication providers, password encoding, and security filters.
- **application.yml** or **application.properties**: Contains security-related properties such as secret keys, token expiration times, and user roles.

Example snippet (hypothetical, based on typical Spring Boot security setup):

```yaml
spring:
  security:
    user:
      name: user
      password: password
jwt:
  secret: your_jwt_secret_key
  expiration: 3600000  # 1 hour in milliseconds
```

---

### 3.4 Usage

- **Login**: Users submit credentials to the authentication endpoint (e.g., `/auth/login`).
- **Token Issuance**: On successful authentication, the system issues a JWT token.
- **Token Usage**: Clients include the JWT token in the `Authorization` header (`Bearer <token>`) for subsequent requests.
- **Token Validation**: Security filters validate tokens on each request to secure endpoints.
- **Logout/Token Revocation**: Depending on implementation, tokens can be revoked or expired.

---

## 4. API Documentation

The authentication module typically exposes the following REST endpoints:

| Endpoint             | Method | Description                                | Request Body                      | Response                    |
|----------------------|--------|--------------------------------------------|---------------------------------|-----------------------------|
| `/auth/login`        | POST   | Authenticates user and returns JWT token  | `{ "username": "", "password": "" }` | `{ "token": "<jwt_token>" }` |
| `/auth/refresh`      | POST   | Refreshes JWT token                        | `{ "refreshToken": "" }`         | `{ "token": "<new_jwt_token>" }` |
| `/auth/logout`       | POST   | Invalidates user session/token             | (Optional)                      | `{ "message": "Logged out" }` |

**Note:** Actual endpoints and payloads depend on the implementation details in the codebase.

---

## 5. Usage Examples

### Example: Authenticate and Access Portfolio API

1. **Login Request**

```bash
curl -X POST https://api.am-portfolio.com/auth/login \
-H "Content-Type: application/json" \
-d '{"username":"john_doe","password":"password123"}'
```

Response:

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

2. **Access Protected Portfolio Endpoint**

```bash
curl -X GET https://api.am-portfolio.com/api/portfolio/summary \
-H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

Response:

```json
{
  "portfolioId": "abc123",
  "summary": { ... }
}
```

---

## 6. Architecture Notes

- **Security Layer**: The authentication module sits as a security layer in front of portfolio APIs, integrated via Spring Security filters.
- **Token-based Authentication**: The system likely uses JWT tokens for stateless authentication, enhancing scalability and decoupling session management.
- **Integration with Redis**: Redis caching is used for portfolio data, but may also be leveraged for token blacklisting or session management if implemented.
- **Kafka and MongoDB**: Authentication is orthogonal to Kafka messaging and MongoDB persistence but ensures secure access to data flowing through these components.
- **Configuration Management**: Security credentials and keys are managed via environment variables or secure configuration files (`application.yml`), following best practices.

---

# Appendix: Partial Redis Configuration Overview (Context)

The `RedisConfig.java` file configures Redis connections and templates for caching portfolio data, stock prices, and market indices. This caching layer improves performance by reducing database load and accelerating data retrieval.

Example snippet from `RedisConfig.java`:

```java
@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.password}")
    private String redisPassword;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, 6379);
        redisConfig.setPassword(redisPassword);
        return new LettuceConnectionFactory(redisConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        // serializers setup...
        return template;
    }
}
```

---

# Summary

This documentation provides a focused look at the **authentication module** within the AM-Portfolio codebase, detailing its role, configuration, usage, and integration points. For full understanding, developers should also review the security configuration classes, controller implementations, and related resources in the repository.