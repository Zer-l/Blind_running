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
        listOf("发起跑步请求", "发起请求", "创建请求", "开始跑步", "我要跑步", "想跑步", "找志愿者"),
    ),
    VIEW_HISTORY(
        R.string.voice_command_label_view_history,
        listOf("查看历史", "跑步历史", "历史记录", "我跑过的", "看历史"),
    ),
    PROFILE(
        R.string.voice_command_label_profile,
        listOf("个人中心", "我的", "设置", "我的资料"),
    ),
    GO_HOME(
        R.string.voice_command_label_go_home,
        listOf("返回首页", "回首页", "回到首页", "主页"),
    ),

    // ===== 全局动作 =====
    CALL_PEER(
        R.string.voice_command_label_call_peer,
        listOf("打电话", "拨电话", "联系志愿者", "联系搭档", "呼叫", "打个电话"),
    ),
    SOS(
        R.string.voice_command_label_sos,
        listOf("紧急求助", "救命", "报警", "求助", "我需要帮助"),
    ),
    STATUS(
        R.string.voice_command_label_status,
        listOf("当前状态", "现在状态", "现在什么状态", "现在到哪了", "进度"),
    ),
    HELP(
        R.string.voice_command_label_help,
        listOf("帮助", "有什么指令", "怎么用", "指令列表"),
    ),

    // ===== 上下文通用动作（由当前 Fragment 处理） =====
    CONFIRM(
        R.string.voice_command_label_confirm,
        listOf("确认", "确定", "是的", "好的", "可以", "对"),
    ),
    CANCEL(
        R.string.voice_command_label_cancel,
        listOf("取消", "返回", "退出", "不要了", "上一页"),
    ),
    SAVE(
        R.string.voice_command_label_save,
        listOf("保存", "提交", "完成", "存储"),
    ),
    MODIFY_REQUEST(
        R.string.voice_command_label_modify_request,
        listOf("修改参数", "修改请求", "修改", "编辑请求"),
    ),
    SKIP(
        R.string.voice_command_label_skip,
        listOf("跳过", "跳过评价", "不评价", "略过"),
    ),
    RETRY(
        R.string.voice_command_label_retry,
        listOf("重试", "重新加载", "再试一次", "再来一次"),
    ),
    REFRESH(
        R.string.voice_command_label_refresh,
        listOf("刷新", "下拉刷新", "重新刷新", "更新"),
    ),

    // ===== 跑步相关 =====
    PAUSE_RUN(
        R.string.voice_command_label_pause_run,
        listOf("暂停跑步", "暂停", "歇一下", "停一下"),
    ),
    END_RUN(
        R.string.voice_command_label_end_run,
        listOf("申请结束", "结束跑步", "跑完了", "结束"),
    ),

    // ===== 时长选择（CreateRequest / EditRequest） =====
    DURATION_30(
        R.string.voice_command_label_duration_30,
        listOf("三十分钟", "30分钟", "半小时"),
    ),
    DURATION_60(
        R.string.voice_command_label_duration_60,
        listOf("六十分钟", "60分钟", "一小时", "一个小时"),
    ),
    DURATION_90(
        R.string.voice_command_label_duration_90,
        listOf("九十分钟", "90分钟", "一个半小时"),
    ),
    DURATION_120(
        R.string.voice_command_label_duration_120,
        listOf("一百二十分钟", "120分钟", "两小时", "两个小时"),
    ),

    // ===== 评分选择（BlindReview） =====
    RATE_1(
        R.string.voice_command_label_rate_1,
        listOf("一星", "一颗星", "1星"),
    ),
    RATE_2(
        R.string.voice_command_label_rate_2,
        listOf("二星", "两星", "两颗星", "2星"),
    ),
    RATE_3(
        R.string.voice_command_label_rate_3,
        listOf("三星", "三颗星", "3星"),
    ),
    RATE_4(
        R.string.voice_command_label_rate_4,
        listOf("四星", "四颗星", "4星"),
    ),
    RATE_5(
        R.string.voice_command_label_rate_5,
        listOf("五星", "五颗星", "满分", "5星"),
    ),

    // ===== 历史筛选（BlindHistory） =====
    FILTER_FINISHED(
        R.string.voice_command_label_filter_finished,
        listOf("筛选已完成", "只看已完成", "已完成", "完成的"),
    ),
    FILTER_CANCELLED(
        R.string.voice_command_label_filter_cancelled,
        listOf("筛选已取消", "只看已取消", "已取消", "取消的"),
    ),
    FILTER_ALL(
        R.string.voice_command_label_filter_all,
        listOf("显示全部", "查看全部", "全部记录", "所有"),
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
