-- 第二个志愿者测试账号（用于 phase2-acceptance 2.x 抢单冲突场景）
-- 使用 INSERT IGNORE 保证幂等，重复执行不报错

INSERT IGNORE INTO users (id, phone, nickname, gender, roles, volunteer_profile, status)
VALUES (
    '00000000-0000-0000-0000-000000000011',
    '13900000011',
    '志愿者小李',
    'FEMALE',
    '["VOLUNTEER"]',
    '{"averagePaceSeconds":380,"runningLevel":"INTERMEDIATE","hasGuideExperience":false,"trainingCompleted":true}',
    'ACTIVE'
);
