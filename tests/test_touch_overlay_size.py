import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GAME_PREFS = ROOT / "android-launcher/app/src/main/java/com/gow3/android/core/settings/GamePrefs.kt"
OVERLAY = ROOT / "android-launcher/app/src/main/java/com/gow3/android/game/TouchControllerOverlay.java"


class OverlaySizePrefsContractTest(unittest.TestCase):
    """GamePrefs 尺寸持久化：缺省 1.0（向后兼容）、clamp、reset 一并清除。"""

    def test_overlay_size_defaults_to_one_for_legacy_layouts(self):
        source = GAME_PREFS.read_text()
        self.assertIn('fun overlaySize(name: String, default: Float = 1f)', source)
        self.assertIn('OVERLAY_KEY_PREFIX + "s_" + name', source)

    def test_set_overlay_size_clamps(self):
        source = GAME_PREFS.read_text()
        self.assertIn("value.coerceIn(0.6f, 2f)", source)

    def test_clear_overlay_layout_covers_size_keys(self):
        """clearOverlayLayout 按 overlay_ 前缀清除，s_ 键随之删除（reset 恢复默认）。"""
        source = GAME_PREFS.read_text()
        self.assertIn('it.startsWith(OVERLAY_KEY_PREFIX)', source)


class OverlaySizeEditingContractTest(unittest.TestCase):
    """编辑态双指捏合缩放（拖住控件 + 第二指捏合，参考 Bloodborne-Android 实现）。"""

    def test_sizes_loaded_from_prefs_in_constructor(self):
        source = OVERLAY.read_text()
        self.assertIn("sizes.put(name,prefs.overlaySize(name,1f));", source)
        self.assertIn("private float size(String name)", source)
        self.assertIn("private static float clampSize(float s)", source)
        self.assertIn("Math.max(MIN_SIZE,Math.min(MAX_SIZE,s))", source)
        self.assertIn("MIN_SIZE=0.6f, MAX_SIZE=2f;", source)

    def test_pinch_lifecycle_and_scaling(self):
        source = OVERLAY.read_text()
        # 第二指按下进入捏合；松开捏合指退出；拖拽指松开时持久化尺寸
        self.assertIn("ACTION_POINTER_DOWN && dragged!=null && pinchPointer<0", source)
        self.assertIn("pinchDistance=pinchDistance(e); pinchSize=size(dragged);", source)
        self.assertIn("sizes.put(dragged,clampSize(pinchSize*d/pinchDistance));", source)
        self.assertIn("ACTION_POINTER_UP && id==pinchPointer", source)
        self.assertIn("prefs.setOverlaySize(dragged,size(dragged));", source)
        # 除零防护
        self.assertIn("pinchDistance>1f", source)

    def test_pinch_cleanup_on_finish_cancel_and_reset(self):
        source = OVERLAY.read_text()
        self.assertIn("editPointer=-1; pinchPointer=-1; dragged=null; editing=false;", source)
        self.assertIn("ACTION_CANCEL) { editPointer=-1; pinchPointer=-1; dragged=null;", source)
        self.assertIn("sizes.put(NAMES[i],1f);", source)


class OverlayScalingConsistencyContractTest(unittest.TestCase):
    """命中/绘制/发布用同一因子，缩放后可点区域与视觉一致。"""

    def test_hit_testing_scales(self):
        source = OVERLAY.read_text()
        self.assertIn("float k=dpadSize();", source)
        self.assertIn("distance(x,y,dpad)<112*k", source)
        self.assertIn("dx<(name.equals(\"touchpad\") ? 72 : 62)*s && dy<38*s", source)
        self.assertIn("stick(name) ? 70 : name.equals(\"l3\") || name.equals(\"r3\") ? 39 : 48)*s", source)
        # 十字键因子 = 四键均值
        self.assertIn('return (size("up")+size("left")+size("right")+size("down"))/4f;', source)

    def test_publish_travel_scales(self):
        source = OVERLAY.read_text()
        self.assertIn("float t=22*dpadSize();", source)
        self.assertEqual(source.count("float range=56*size(control);"), 2,
                         "左右摇杆行程都应随因子缩放")

    def test_drawing_scales(self):
        source = OVERLAY.read_text()
        self.assertIn("PointF p=center(name); float s=size(name); r*=s;", source)
        self.assertIn("float half=width/2,halfH=25*s,radius=14*s;", source)
        self.assertIn("c.drawCircle(center.x,center.y,57*s,paint);", source)
        self.assertIn("float knobX=center.x,knobY=center.y,travel=27*s;", source)
        self.assertIn("c.drawCircle(knobX,knobY,28*s,paint);", source)

    def test_non_size_behaviors_unchanged(self):
        """DONE 条命中区、touchpad→touchpad_right 语义不被本变更影响。"""
        source = OVERLAY.read_text()
        self.assertIn("x>548 && x<732 && y<72", source)
        self.assertIn('pressed.add("touchpad_right")', source)


if __name__ == "__main__":
    unittest.main()
