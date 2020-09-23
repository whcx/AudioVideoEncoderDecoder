#pragma once

#define ANDROID_API_PUBLIC __attribute__((visibility("default")))
#define ANDROID_API_HIDDEN __attribute__((visibility("hidden")))

#define AV_ED_BEGIN namespace AV_ED {
#define AV_ED_END }
#define AV_ED_USING using namespace AV_ED;
