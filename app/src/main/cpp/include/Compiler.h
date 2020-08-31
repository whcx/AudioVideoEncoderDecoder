#pragma once

#ifdef __cplusplus__
#define CC_LIKELY( exp ) (__builtin_expect( !!(exp), true))
#define CC_UNLIKELY( exp ) (__builtin_expect( !!(exp), false))
#else
#define CC_LIKELY( exp ) (__builtin_expect( !!(exp), 1))
#define CC_UNLIKELY( exp ) (__builtin_expect( !!(exp), 0))
#endif

#define ANDROID_API __attribute__((visibility(default)))
