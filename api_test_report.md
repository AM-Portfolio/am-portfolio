# API Test Report: AM Portfolio - Basket Features

## 1. Get Basket Catalog

**Request:** GET http://localhost:8081/api/v1/portfolios/basket/catalog

**Status:** 500

**Response:**
`json
{
  "timestamp": "2026-08-17T10:20:06.826+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/portfolios/basket/catalog"
}
`

## 2. Get Opportunities

**Request:** GET http://localhost:8081/api/v1/portfolios/basket/opportunities?themeId=it_sector

**Status:** 500

**Response:**
`json
{
  "timestamp": "2026-08-17T10:20:06.884+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/portfolios/basket/opportunities"
}
`

## 3. Calculate Quantities

**Request:** POST http://localhost:8081/api/v1/portfolios/basket/calculate-quantities

**Payload:**
`json
{
  "investmentAmount": 100000,
  "opportunity": {}
}
`

**Status:** 500

**Response:**
`json
{
  "timestamp": "2026-08-17T10:20:06.928+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/portfolios/basket/calculate-quantities"
}
`

## 4. Create Portfolio

**Request:** POST http://localhost:8081/api/v1/portfolios/basket/create-portfolio

**Payload:**
`json
{
  "opportunity": {},
  "portfolioName": "My First Basket",
  "userId": "e6bd35a2-97b7-41b1-aef4-e34360e22b07"
}
`

**Status:** 500

**Response:**
`json
{
  "timestamp": "2026-08-17T10:20:06.956+00:00",
  "status": 500,
  "error": "Internal Server Error",
  "path": "/api/v1/portfolios/basket/create-portfolio"
}
`

