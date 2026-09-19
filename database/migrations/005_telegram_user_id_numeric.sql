-- DARKI Cloud — support Telegram user IDs beyond PostgreSQL BIGINT range.

ALTER TABLE users
    ALTER COLUMN telegram_user_id TYPE NUMERIC(20,0)
    USING telegram_user_id::NUMERIC(20,0);
