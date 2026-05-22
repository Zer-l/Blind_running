package com.guiderun.app.accessibility.voice

import androidx.annotation.StringRes
import com.guiderun.app.R

/**
 * 视障端语音指令枚举。每个指令对应一组同义词短语，由 [CommandParser] 用于匹配。
 *
 * 设计原则：
 * - 全局指令（导航 / SOS / 通话）由 [CommandExecutor] 直接处理
 * - 上下文指令（确认 / 取消 / 保存 / 数值选择 / 评分等）由当前 Fragment 注册的
 *   [VoiceCommandContextHandler] 处理；未注册时由 Manager 反馈"当前页面无法执行"
 * - phrases 全部去除标点和空格后参与匹配；同一指令的多个短语越具体越靠后，便于"最长匹配优先"
 */
enum class VoiceCommand(
    @StringRes val labelRes: Int,
    val phrases: List<String>,
) {
    // ===== 全局导航 =====
    CREATE_REQUEST(
        R.string.voice_command_label_create_request,
        listOf("发起跑步请求", "发起请求", "创建请求", "开始跑步", "我要跑步", "想跑步", "找志愿者",
            "叫人陪跑", "找人陪跑", "约跑步", "叫个志愿者", "找陪跑"),
    ),
    VIEW_HISTORY(
        R.string.voice_command_label_view_history,
        listOf("查看历史", "跑步历史", "历史记录", "我跑过的", "看历史", "历史", "之前的记录",
            "以前的记录", "看记录"),
    ),
    PROFILE(
        R.string.voice_command_label_profile,
        listOf("个人中心", "我的", "我的资料", "个人信息", "用户中心"),
    ),
    GO_HOME(
        R.string.voice_command_label_go_home,
        listOf("返回首页", "回首页", "回到首页", "主页", "首页", "回去", "回主页"),
    ),

    // ===== 全局动作 =====
    CALL_PEER(
        R.string.voice_command_label_call_peer,
        listOf("打电话", "拨电话", "联系志愿者", "联系搭档", "呼叫", "打个电话",
            "通话", "接通", "拨号", "联系跑友", "联系跑者", "打他电话"),
    ),
    SOS(
        R.string.voice_command_label_sos,
        listOf("紧急求助", "救命", "报警", "求助", "我需要帮助", "出事了", "出问题了",
            "紧急", "sos", "救救我"),
    ),
    STATUS(
        R.string.voice_command_label_status,
        listOf(
            // 状态查询（与 hint 引导语严格对齐；单字"查"易误触不收）
            "状态", "状态查询", "查询状态", "查询", "查一下",
            // 通用进度/位置
            "当前状态", "现在状态", "现在什么状态", "现在到哪了", "进度",
            "到哪了", "还有多远", "距离", "位置", "现在多少", "汇报一下",
            // 等待时长查询（WaitingMatch 命中率提升）
            "等多久", "等了多久", "还要等多久", "还有多久", "等待时长",
            "等多长", "等多长时间", "还要等多长", "还要等多长时间", "等了多长时间",
            "等了多少", "剩余时间", "还要多长时间", "已经等了多久",
            // 运动数据
            "时长", "跑了多久", "跑了多远",
        ),
    ),
    HELP(
        R.string.voice_command_label_help,
        listOf("帮助", "有什么指令", "怎么用", "指令列表", "什么指令", "怎么操作",
            "能说什么", "都能说什么", "命令"),
    ),

    // ===== 上下文通用动作（由当前 Fragment 处理） =====
    CONFIRM(
        R.string.voice_command_label_confirm,
        listOf("确认", "确定", "是的", "好的", "可以", "对", "执行", "开始", "行",
            "嗯", "ok"),
    ),
    CANCEL(
        R.string.voice_command_label_cancel,
        listOf("取消", "返回", "退出", "不要了", "上一页", "算了", "不要", "我不想",
            "撤销", "作废", "不"),
    ),
    SAVE(
        R.string.voice_command_label_save,
        listOf("保存", "提交", "完成", "存储", "存", "保存修改", "保存资料"),
    ),
    SKIP(
        R.string.voice_command_label_skip,
        listOf("跳过", "跳过评价", "不评价", "略过", "不评了", "下次再说"),
    ),
    RETRY(
        R.string.voice_command_label_retry,
        listOf("重试", "重新加载", "再试一次", "再来一次", "再来", "再试", "重来"),
    ),
    REFRESH(
        R.string.voice_command_label_refresh,
        listOf("刷新", "下拉刷新", "重新刷新", "更新", "再刷新", "重新加载列表"),
    ),

    // ===== 跑步相关 =====
    PAUSE_RUN(
        R.string.voice_command_label_pause_run,
        listOf("暂停跑步", "暂停", "歇一下", "停一下", "歇歇", "停停", "缓一下"),
    ),
    RESUME_RUN(
        R.string.voice_command_label_resume_run,
        listOf("继续跑步", "继续", "恢复", "恢复跑步", "接着跑", "继续走"),
    ),
    END_RUN(
        R.string.voice_command_label_end_run,
        listOf("申请结束", "结束跑步", "跑完了", "结束", "我跑完了", "到了",
            "不跑了", "停止", "终止"),
    ),

    // ===== 时长选择（CreateRequest 页） =====
    DURATION_30(
        R.string.voice_command_label_duration_30,
        listOf("三十分钟", "30分钟", "半小时", "半个钟", "半小时跑", "三十"),
    ),
    DURATION_60(
        R.string.voice_command_label_duration_60,
        listOf("六十分钟", "60分钟", "一小时", "一个小时", "一个钟", "一个钟头", "六十"),
    ),
    DURATION_90(
        R.string.voice_command_label_duration_90,
        listOf("九十分钟", "90分钟", "一个半小时", "九十", "一小时半"),
    ),
    DURATION_120(
        R.string.voice_command_label_duration_120,
        listOf("一百二十分钟", "120分钟", "两小时", "两个小时", "两个钟", "两个钟头", "一百二十"),
    ),

    // ===== 评分选择（BlindReview） =====
    RATE_1(
        R.string.voice_command_label_rate_1,
        listOf("一星", "一颗星", "1星", "一分", "最差", "差评", "差"),
    ),
    RATE_2(
        R.string.voice_command_label_rate_2,
        listOf("二星", "两星", "两颗星", "2星", "两分", "二分"),
    ),
    RATE_3(
        R.string.voice_command_label_rate_3,
        listOf("三星", "三颗星", "3星", "三分", "一般"),
    ),
    RATE_4(
        R.string.voice_command_label_rate_4,
        listOf("四星", "四颗星", "4星", "四分", "不错"),
    ),
    RATE_5(
        R.string.voice_command_label_rate_5,
        listOf("五星", "五颗星", "满分", "5星", "五分", "好评", "最好", "最高分"),
    ),

    // ===== 历史筛选（BlindHistory） =====
    FILTER_FINISHED(
        R.string.voice_command_label_filter_finished,
        listOf("筛选已完成", "只看已完成", "已完成", "完成的", "已结束", "完成记录", "成功的"),
    ),
    FILTER_CANCELLED(
        R.string.voice_command_label_filter_cancelled,
        listOf("筛选已取消", "只看已取消", "已取消", "取消的", "失败的", "作废", "未完成"),
    ),
    FILTER_ALL(
        R.string.voice_command_label_filter_all,
        listOf("显示全部", "查看全部", "全部记录", "所有", "全部", "全显示", "所有记录"),
    ),

    // ===== Settings 子页 =====
    OPEN_PROFILE_EDIT(
        R.string.voice_command_label_open_profile_edit,
        listOf("编辑资料", "修改资料", "个人资料编辑", "编辑个人资料"),
    ),
    OPEN_EMERGENCY_CONTACTS(
        R.string.voice_command_label_open_emergency_contacts,
        listOf("紧急联系人", "紧急联系", "联系人列表"),
    ),
    OPEN_STATS(
        R.string.voice_command_label_open_stats,
        listOf("跑步统计", "我的数据", "统计数据", "运动数据"),
    ),
    OPEN_ACCESSIBILITY(
        R.string.voice_command_label_open_accessibility,
        listOf("无障碍设置", "无障碍", "辅助设置"),
    ),

    // ===== 联系人列表 =====
    ADD_CONTACT(
        R.string.voice_command_label_add_contact,
        listOf("添加联系人", "新增联系人", "增加联系人", "添加紧急联系人"),
    ),

    // ===== 无障碍设置 =====
    SPEED_FASTER(
        R.string.voice_command_label_speed_faster,
        listOf("说快点", "语速加快", "快一点", "更快"),
    ),
    SPEED_SLOWER(
        R.string.voice_command_label_speed_slower,
        listOf("说慢点", "语速减慢", "慢一点", "更慢"),
    ),
    ;
}
