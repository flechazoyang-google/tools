package com.flechazo.toolbox.feature.countdown

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 农历换算的实现绑定。
 *
 * 走接口而不是直接用 [TableLunarCalendar]：算法要能被单测替换成假实现（`CountdownEngineTest`
 * 里就用了一个固定输出的 fake），也要允许将来换库（例如引入几百 KB 的精确天文历表）
 * 而不动到 ViewModel 与调度层。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CountdownModule {

    @Binds
    @Singleton
    abstract fun bindLunarCalendar(impl: TableLunarCalendar): LunarCalendar
}
