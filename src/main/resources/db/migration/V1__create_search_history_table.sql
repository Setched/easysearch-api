CREATE TABLE search_history (
    id              BIGSERIAL PRIMARY KEY,
    query           VARCHAR(255)   NOT NULL,
    total_offers    INTEGER        NOT NULL,
    best_marketplace VARCHAR(50),
    best_price      NUMERIC(19, 2),
    searched_at     TIMESTAMP      NOT NULL
);

CREATE INDEX idx_search_history_query ON search_history (query);
