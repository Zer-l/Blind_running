-- 仅在 dev profile 执行，勿在生产环境运行

INSERT INTO users (id, phone, nickname, gender, roles, blind_profile, status)
VALUES
(
    '00000000-0000-0000-0000-000000000001',
    '13800000001',
    '张先生',
    'MALE',
    '["BLIND_RUNNER"]',
    '{"visionLevel":"TOTAL_BLIND","preferredPaceSeconds":420,"emergencyContacts":[{"name":"张妻","phone":"13800000011","relationship":"配偶"}],"visualDescription":"身高170，中等身材"}',
    'ACTIVE'
),
(
    '00000000-0000-0000-0000-000000000002',
    '13800000002',
    '李阿姨',
    'FEMALE',
    '["BLIND_RUNNER"]',
    '{"visionLevel":"LOW_VISION","preferredPaceSeconds":540,"emergencyContacts":[{"name":"李子","phone":"13800000012","relationship":"儿子"}],"visualDescription":"身高160，常戴墨镜"}',
    'ACTIVE'
),
(
    '00000000-0000-0000-0000-000000000010',
    '13900000010',
    '跑者小王',
    'MALE',
    '["VOLUNTEER"]',
    NULL,
    'ACTIVE'
);

UPDATE users
SET volunteer_profile = '{"averagePaceSeconds":360,"runningLevel":"INTERMEDIATE","hasGuideExperience":true,"trainingCompleted":true}'
WHERE id = '00000000-0000-0000-0000-000000000010';
