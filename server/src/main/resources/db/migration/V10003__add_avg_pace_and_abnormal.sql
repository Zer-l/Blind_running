ALTER TABLE run_requests
    ADD COLUMN avg_pace_seconds INT       NULL COMMENT '结束跑步时客户端上报的平均配速(秒/公里)',
    ADD COLUMN is_abnormal      BOOLEAN   NOT NULL DEFAULT FALSE COMMENT '距离>50km或时长>5h标记为异常';
