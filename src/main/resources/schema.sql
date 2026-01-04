PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS runs (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  requested_at  TEXT NOT NULL,
  ship_code     TEXT NOT NULL,
  sail_date     TEXT NOT NULL,
  adults        INTEGER NOT NULL,
  children      INTEGER NOT NULL,
  cabin_class   TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS offers (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  run_id        INTEGER NOT NULL,
  vendor        TEXT NOT NULL,
  ship_code     TEXT NOT NULL,
  sail_date     TEXT NOT NULL,
  cabin_class   TEXT NOT NULL,
  nights        INTEGER NOT NULL,
  price_cents   INTEGER NOT NULL,
  currency      TEXT NOT NULL,
  retrieved_at  TEXT NOT NULL,
  raw_offer_id  TEXT NOT NULL,
  FOREIGN KEY (run_id) REFERENCES runs(id)
);

CREATE TABLE IF NOT EXISTS vendor_health (
  vendor             TEXT PRIMARY KEY,
  last_success_at    TEXT,
  last_error_at      TEXT,
  last_error_message TEXT
);