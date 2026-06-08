-- ============================================
-- GugleChat 测试种子数据 (PostgreSQL)
-- 注意: 密码为 BCrypt 哈希，原始密码均为 "123456"
-- ============================================

-- 测试用户 (password = "123456")
INSERT INTO users (username, email, password_hash, nickname, status) VALUES
('alice',   'alice@example.com',   '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBk.TmSvLzq/r6GdOoTqLqC/k6W', 'Alice',   'OFFLINE'),
('bob',     'bob@example.com',     '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBk.TmSvLzq/r6GdOoTqLqC/k6W', 'Bob',     'OFFLINE'),
('charlie', 'charlie@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjqQBk.TmSvLzq/r6GdOoTqLqC/k6W', 'Charlie', 'OFFLINE')
ON CONFLICT (username) DO NOTHING;

-- 测试频道
INSERT INTO channels (id, name, description, type, created_by) VALUES
(1, 'general',   'General discussion',          'TEXT',  1),
(2, 'random',    'Random topics',               'TEXT',  1),
(3, 'voice-chat','Voice & video chat room',     'VOICE', 1)
ON CONFLICT (id) DO NOTHING;

-- 重置序列
SELECT setval('channels_id_seq', (SELECT MAX(id) FROM channels));

-- 频道成员
INSERT INTO channel_members (channel_id, user_id, role) VALUES
(1, 1, 'OWNER'),
(1, 2, 'MEMBER'),
(1, 3, 'MEMBER'),
(2, 1, 'OWNER'),
(2, 2, 'ADMIN'),
(3, 1, 'OWNER'),
(3, 2, 'MEMBER'),
(3, 3, 'MEMBER')
ON CONFLICT (channel_id, user_id) DO NOTHING;
