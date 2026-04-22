-- Create portfolios table
CREATE TABLE portfolios (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner VARCHAR(255),
    currency VARCHAR(10),
    fundType VARCHAR(100) DEFAULT 'DEFAULT',
    status VARCHAR(50),
    total_value DOUBLE PRECISION,
    asset_count INTEGER,
    tags TEXT,
    notes TEXT,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version INTEGER NOT NULL DEFAULT 0 
);

-- Create assets table
CREATE TABLE assets (
    id UUID PRIMARY KEY,
    symbol VARCHAR(50) NOT NULL,
    isin VARCHAR(12),
    name VARCHAR(255),
    description TEXT,
    portfolio_id UUID,
    asset_type VARCHAR(50) NOT NULL,
    quantity DOUBLE PRECISION,
    avg_buying_price DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    investment_value DOUBLE PRECISION,
    broker_type VARCHAR(100),
    exchange VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    currency VARCHAR(10),
    country VARCHAR(100),
    tags TEXT,
    notes TEXT,
    status VARCHAR(50),
    purchase_date TIMESTAMP,
    maturity_date TIMESTAMP,
    created_at TIMESTAMP,
    modified_at TIMESTAMP,
    FOREIGN KEY (portfolio_id) REFERENCES portfolios(id)
);

-- Create equity table (subclass of Asset)
CREATE TABLE equity (
    id UUID PRIMARY KEY,
    market_cap DOUBLE PRECISION,
    pe_ratio DOUBLE PRECISION,
    dividend_yield DOUBLE PRECISION,
    FOREIGN KEY (id) REFERENCES assets(id)
);

-- Insert sample portfolio data


INSERT INTO portfolios (id, name, description, owner, currency, status, created_at, updated_at)
VALUES 
    ('123e4567-e89b-12d3-a456-426614174000', 'Growth Portfolio', 'Long-term growth investment portfolio', 'MKU257', 'INR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('123e4567-e89b-12d3-a456-426614174001', 'Dividend Portfolio', 'Income focused portfolio', 'MKU257', 'INR', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert sample asset data
INSERT INTO assets (id, symbol, name, portfolio_id, asset_type, quantity, avg_buying_price, current_value, currency, status, created_at, modified_at)
VALUES 
    ('123e4567-e89b-12d3-a456-426614174002', 'AAPL', 'Apple Inc.', '123e4567-e89b-12d3-a456-426614174000', 'EQUITY', 100, 150.00, 175.00, 'USD', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('123e4567-e89b-12d3-a456-426614174003', 'MSFT', 'Microsoft Corporation', '123e4567-e89b-12d3-a456-426614174000', 'EQUITY', 50, 280.00, 310.00, 'USD', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Insert sample equity data
INSERT INTO equity (id, market_cap, pe_ratio, dividend_yield)
VALUES 
    ('123e4567-e89b-12d3-a456-426614174002', 2800000000000, 28.5, 0.65),
    ('123e4567-e89b-12d3-a456-426614174003', 2300000000000, 32.1, 0.85);
