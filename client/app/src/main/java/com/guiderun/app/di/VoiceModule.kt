package com.guiderun.app.di

import com.guiderun.app.accessibility.asr.AsrEngine
import com.guiderun.app.accessibility.asr.IflytekAsrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 [AsrEngine] 接口绑定到具体实现。
 *
 * 切换实现：只需把 [bindAsrEngine] 的入参换成另一个 `@Singleton class XxxAsrEngine : AsrEngine`。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindAsrEngine(impl: IflytekAsrEngine): AsrEngine
}
