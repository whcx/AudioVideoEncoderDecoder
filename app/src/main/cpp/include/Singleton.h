//
// Created by Jianlin on 21/09/2020.
//
#pragma once

#include "Log.h"
#include "Compiler.h"

AV_ED_BEGIN
    template <typename TYPE>
    class ANDROID_API_PUBLIC Singleton {
    public:
        static TYPE& GetInstance() {
            static TYPE instance;
            return instance;
        }

    protected:
        virtual ~Singleton() = default;
        Singleton() = default;

    private:
        Singleton(const Singleton&) = delete;
        Singleton& operator = (const Singleton&) = delete;
    };

AV_ED_END