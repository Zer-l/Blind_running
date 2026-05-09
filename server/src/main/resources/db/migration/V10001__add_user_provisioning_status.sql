ALTER TABLE users
ADD COLUMN provisioning_status ENUM('PENDING_ROLE', 'ACTIVE') NOT NULL DEFAULT 'ACTIVE'
AFTER status;

-- 已有正常账号保持 ACTIVE
UPDATE users SET provisioning_status = 'ACTIVE' WHERE JSON_LENGTH(roles) > 0;

-- 没有角色的账号（脏数据或新注册未完成）标为 PENDING_ROLE
UPDATE users SET provisioning_status = 'PENDING_ROLE' WHERE JSON_LENGTH(roles) = 0;
