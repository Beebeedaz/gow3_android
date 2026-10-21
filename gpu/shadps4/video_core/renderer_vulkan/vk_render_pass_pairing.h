// SPDX-License-Identifier: GPL-2.0-or-later
#pragma once

#include <atomic>
#include <cstdint>

namespace Vulkan {
/// gow3: 渲染范围配对记账的纯逻辑组件（fix-renderpass-pairing-across-buffers）。
/// Scheduler 与宿主行为测试共用同一实现：不依赖 Vulkan，深度只记"记录侧意图"。
///
/// 语义对齐 spec：
///  - 任一 command buffer 代内，Begin 与 End 配对；End 不得出现在没有对应 Begin 的代（跨代错位）。
///  - OnSwap 在换代时调用，把深度归 0：若 Begin 落在旧代而孤立 End 落在新代，OnEnd 返回 false，
///    调用方应告警并跳过该 End（护栏），而不是把无 Begin 的 End 交给驱动（Turnip 会解引用空
///    subpass，fault 0x4e）。
///  - 深度是并发下的保守下界：只拦确定非法的 End，不误伤合法配对。
class RenderPassPairing {
public:
    /// 换代：新 command buffer 代深度归 0。返回上一代是否残留未闭合范围（调用方据以收口/告警）。
    void OnSwap() {
        depth.store(0, std::memory_order_relaxed);
    }

    /// Begin 被录制：递增深度，返回新深度（>0 表示当前代有 open pass）。
    std::uint32_t OnBegin() {
        return depth.fetch_add(1, std::memory_order_relaxed) + 1;
    }

    /// End 请求：若当前代深度为 0（没有对应 Begin，跨代错位）返回 false，调用方应告警并跳过该
    /// End；否则递减并返回 true，调用方照常录制 endRendering。
    bool OnEnd() {
        if (depth.load(std::memory_order_relaxed) == 0) {
            return false;
        }
        depth.fetch_sub(1, std::memory_order_relaxed);
        return true;
    }

    /// 当前代深度。
    [[nodiscard]] std::uint32_t Depth() const {
        return depth.load(std::memory_order_relaxed);
    }

private:
    std::atomic<std::uint32_t> depth{0};
};

} // namespace Vulkan
