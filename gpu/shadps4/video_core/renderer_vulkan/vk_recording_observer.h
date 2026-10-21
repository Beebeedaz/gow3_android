// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include "video_core/renderer_vulkan/vk_common.h"
#include "video_core/renderer_vulkan/vk_recording_diagnostics.h"

// gow3: 默认关闭的录制诊断埋点（GOW3_PRODUCER_CHECK / GOW3_RECORDING_CHECK /
// GOW3_RENDERPASS_CHECK，见 my-docs/01-环境变量/GPU录制诊断.md）。与旧分支
// fix-concurrent-renderpass-recording 的 observer 不同：本 change 不改 CommandBuffer() 的返回
// 类型契约（旧版返回 ObservedCommandBuffer），只用函数式埋点接入 scheduler 的录制入口、代标识
// 与 Begin/End 配对，保持调用点 diff 最小。

namespace Vulkan {

inline uintptr_t RecordingBufferId(vk::CommandBuffer buffer) {
    return reinterpret_cast<uintptr_t>(static_cast<VkCommandBuffer>(buffer));
}

/// 录制入口的 RAII 观测：构造进入（登记活动区间，重叠时报告），析构离开。调用点用
/// const auto 保持对象存活到录制结束，否则范围被静默缩短。
inline RecordingDiagnostics::Scope ObserveProducer(const char* entry) {
    auto& tracker = RecordingDiagnostics::Get();
    return {tracker, RecordingDiagnostics::Kind::Producer,
            {reinterpret_cast<uintptr_t>(&tracker), 0}, entry};
}

inline void ObserveRenderBegin(vk::CommandBuffer buffer, const char* entry) {
    RecordingDiagnostics::Get().Rendering(RecordingBufferId(buffer), true, entry);
}

inline void ObserveRenderEnd(vk::CommandBuffer buffer, const char* entry) {
    RecordingDiagnostics::Get().Rendering(RecordingBufferId(buffer), false, entry);
}

/// 每次 AllocateWorkerCommandBuffers 取得新 command buffer 后登记新代（buffer 身份 + 递增代次）。
inline void ObserveNewSequence(vk::CommandBuffer buffer) {
    RecordingDiagnostics::Get().NewSequence(RecordingBufferId(buffer));
}

/// SubmitExecution 在 cmdbuf.end() 前登记序列结束（带未闭合范围提交会被配对记账报告）。
inline void ObserveSubmit(vk::CommandBuffer buffer) {
    RecordingDiagnostics::Get().EndSequence(RecordingBufferId(buffer));
}

} // namespace Vulkan
