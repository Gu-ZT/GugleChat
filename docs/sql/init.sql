-- ============================================
-- GugleChat 数据库初始化脚本 (PostgreSQL)
-- ============================================

-- 1. 用户表
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    email           VARCHAR(100)    NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    avatar_url      VARCHAR(500),
    nickname        VARCHAR(50),
    status          VARCHAR(10)     NOT NULL DEFAULT 'OFFLINE',
    last_seen_at    TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  users              IS '用户表';
COMMENT ON COLUMN users.username     IS '用户名，唯一';
COMMENT ON COLUMN users.email        IS '邮箱，唯一';
COMMENT ON COLUMN users.password_hash IS 'BCrypt 密码哈希';
COMMENT ON COLUMN users.status       IS 'ONLINE|OFFLINE|BUSY|AWAY';
COMMENT ON COLUMN users.created_at   IS '注册时间';

-- 2. 频道表
CREATE TABLE IF NOT EXISTS channels (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(100)    NOT NULL,
    description     TEXT,
    type            VARCHAR(10)     NOT NULL DEFAULT 'TEXT',
    created_by      BIGINT          NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  channels           IS '频道表';
COMMENT ON COLUMN channels.type      IS 'TEXT 文字频道 | VOICE 语音频道';
COMMENT ON COLUMN channels.created_by IS '创建者 user.id';

-- 3. 频道成员表
CREATE TABLE IF NOT EXISTS channel_members (
    id              BIGSERIAL       PRIMARY KEY,
    channel_id      BIGINT          NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role            VARCHAR(10)     NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP       NOT NULL DEFAULT NOW(),

    UNIQUE (channel_id, user_id)
);

COMMENT ON TABLE  channel_members        IS '频道成员表';
COMMENT ON COLUMN channel_members.role   IS 'OWNER|ADMIN|MEMBER';

-- 4. 消息表
CREATE TABLE IF NOT EXISTS messages (
    id              BIGSERIAL       PRIMARY KEY,
    channel_id      BIGINT          NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    user_id         BIGINT          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content         TEXT            NOT NULL,
    type            VARCHAR(20)     NOT NULL DEFAULT 'TEXT',
    parent_id       BIGINT          REFERENCES messages(id) ON DELETE SET NULL,
    edited_at       TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  messages             IS '消息表';
COMMENT ON COLUMN messages.content     IS '消息内容 (Markdown)';
COMMENT ON COLUMN messages.type        IS 'TEXT|IMAGE|VIDEO|AUDIO|FILE|LINK_EMBED';
COMMENT ON COLUMN messages.parent_id   IS '回复/线程父消息ID';

-- 索引
CREATE INDEX IF NOT EXISTS idx_messages_channel_created
    ON messages (channel_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_messages_channel_before
    ON messages (channel_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_channel_members_user
    ON channel_members (user_id);

CREATE INDEX IF NOT EXISTS idx_channels_updated
    ON channels (updated_at DESC);
