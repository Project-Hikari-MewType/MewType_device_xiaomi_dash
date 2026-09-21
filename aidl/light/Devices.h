/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

#pragma once

#include <models/IDumpable.h>
#include <models/State.h>

#include <cstdint>
#include <string>

namespace aidl {
namespace android {
namespace hardware {
namespace light {

// AW21024 ring LED on dash, driven through the high-level sysfs API
// verified against the stock leds-color21024.ko driver:
//   /sys/class/leds/aw21024_led/{hwen,run,color,rgbcolor,period,repeat,brightness,gradient,effect}
// rgbcolor parses "%x %x" (zone + color), color parses "%x",
// period parses "%d %d %d %d", run/repeat/hwen/gradient take a single uint
// (run: 0=off, 1=static, 2=breath, 3=gradient). The ring covers all 8
// pixels; static frames program every segment so zones 0-3 (pixels 3-6)
// always light up.
class Devices : public IDumpable {
  public:
    Devices();

    void dump(int fd) const override;

    bool hasNotificationDevices() const;

    void setNotificationState(const State& state);

  private:
    bool setBreath(const std::string& color, uint32_t riseMs, uint32_t onMs, uint32_t fallMs,
                   uint32_t offMs);
    bool setStatic(const std::string& color, uint32_t brightness);

    std::string mBasePath = "/sys/class/leds/aw21024_led/";
};

}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
