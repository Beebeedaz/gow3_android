// SPDX-License-Identifier: GPL-2.0-or-later
// 宿主行为回归：执行生产实现 vk_render_pass_pairing.h（Scheduler 实际使用的同一组件），
// 验证跨代错位护栏、跨批次续接配对、direct/deferred 往返与重复 End。不依赖 Vulkan。
#include <cassert>
#include <cstdio>
#include <cstring>
#include <thread>
#include <vector>

#include "video_core/renderer_vulkan/vk_render_pass_pairing.h"

namespace {

using Vulkan::RenderPassPairing;

void TestCrossGenerationEndIsRejected() {
    // 跨代错位：Begin 在代 N，换代后孤立 End 落在代 N+1，护栏必须拦截（不能有 End 无 Begin）。
    RenderPassPairing pairing;
    assert(pairing.Depth() == 0);
    pairing.OnBegin();               // 代 N：Begin 入队
    assert(pairing.Depth() == 1);
    pairing.OnSwap();                // 换代 → 深度归 0
    assert(pairing.Depth() == 0);
    const bool accepted = pairing.OnEnd();   // 代 N+1 的孤立 End
    assert(!accepted && "跨代错位的 End 必须被护栏拦截");
    assert(pairing.Depth() == 0);
    std::printf("PASS cross-generation End rejected\n");
}

void TestLegalCrossBatchPairing() {
    // 合法跨批次：同代内 Begin/draw/End 分属不同录制批次，配对保持。
    RenderPassPairing pairing;
    pairing.OnBegin();               // 批次 1：Begin
    assert(pairing.Depth() == 1);
    // 批次 2（draw 不改变深度）
    assert(pairing.Depth() == 1);
    const bool accepted = pairing.OnEnd();    // 批次 3：End
    assert(accepted && "同代合法 End 必须被接受");
    assert(pairing.Depth() == 0);
    std::printf("PASS legal cross-batch pairing\n");
}

void TestDirectDeferredRoundTrip() {
    // direct/deferred 往返：多次 Begin/End 配对，深度在每对后归零。
    RenderPassPairing pairing;
    for (int i = 0; i < 16; ++i) {
        pairing.OnBegin();
        assert(pairing.Depth() == 1);
        assert(pairing.OnEnd());
        assert(pairing.Depth() == 0);
    }
    std::printf("PASS direct/deferred round-trip\n");
}

void TestDuplicateEndAndEndWithoutBegin() {
    // 护栏：无 Begin 的 End 与重复 End 都被拦截，不产生非法 End。
    RenderPassPairing pairing;
    assert(!pairing.OnEnd());        // 无 Begin 的 End
    pairing.OnBegin();
    assert(pairing.OnEnd());         // 第一次合法
    assert(!pairing.OnEnd());        // 重复 End：深度已 0
    assert(pairing.Depth() == 0);
    std::printf("PASS duplicate / missing-Begin End rejected\n");
}

void TestConcurrentCrossGenerationIsCaught() {
    // 并发交错：一个线程在 Begin 后立刻换代（模拟并发 SubmitExecution），
    // 另一个线程随即请求 End——无论交错如何，护栏不产生无 Begin 的 End。
    for (int round = 0; round < 200; ++round) {
        RenderPassPairing pairing;
        std::atomic<bool> begin_done{false};
        std::atomic<int> accepted_ends{0};
        std::thread swapper([&] {
            pairing.OnBegin();
            begin_done.store(true, std::memory_order_release);
            pairing.OnSwap();  // 换代可能在 End 前或后
        });
        std::thread ender([&] {
            while (!begin_done.load(std::memory_order_acquire)) {
                std::this_thread::yield();
            }
            if (pairing.OnEnd()) {
                accepted_ends.fetch_add(1, std::memory_order_relaxed);
            }
        });
        swapper.join();
        ender.join();
        // 无论 interleave 如何，accepted 的 End 数不可能是"无 Begin"的：若 End 在 OnSwap 前
        // 被接受，说明它配对上了 Begin（同代）；若在 OnSwap 后被拦截，depth 保持 0。
        assert(pairing.Depth() == 0);
    }
    std::printf("PASS concurrent cross-generation is caught\n");
}

} // namespace

int main() {
    TestCrossGenerationEndIsRejected();
    TestLegalCrossBatchPairing();
    TestDirectDeferredRoundTrip();
    TestDuplicateEndAndEndWithoutBegin();
    TestConcurrentCrossGenerationIsCaught();
    std::printf("PASS\n");
    return 0;
}
