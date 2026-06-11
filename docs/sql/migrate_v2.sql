-- ============================================
-- GugleChat v2 迁移脚本：为已有数据库添加新字段
-- ============================================

-- users: 移除 status/last_seen_at，添加 role/updated_at/version/flag
ALTER TABLE users
    DROP COLUMN IF EXISTS status,
    DROP COLUMN IF EXISTS last_seen_at,
    ADD COLUMN IF NOT EXISTS role         VARCHAR(20)  NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version      INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flag         INTEGER      NOT NULL DEFAULT 0;

-- channels: 添加 version/flag
ALTER TABLE channels
    ADD COLUMN IF NOT EXISTS version      INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flag         INTEGER      NOT NULL DEFAULT 0;

-- channel_members: 添加 created_at/updated_at/version/flag
ALTER TABLE channel_members
    ADD COLUMN IF NOT EXISTS created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version      INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flag         INTEGER      NOT NULL DEFAULT 0;

-- messages: 添加 updated_at/version/flag
ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS version      INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS flag         INTEGER      NOT NULL DEFAULT 0;
