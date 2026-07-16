package com.vela.app.di

import android.content.Context
import com.vela.app.data.local.EventStore
import com.vela.app.data.repository.DefaultVelaRepository
import com.vela.app.data.repository.VelaRepository

/**
 * 应用级依赖容器。由 [com.vela.app.VelaApplication] 在进程启动时初始化，
 * 之后所有组件（ViewModel、Receiver、Service、Widget）从这里取依赖。
 * ViewModel 通过构造参数默认值引用，测试时可传入替身实现。
 */
object VelaGraph {
    @Volatile
    private var _repository: VelaRepository? = null

    val repository: VelaRepository
        get() = checkNotNull(_repository) {
            "VelaGraph 尚未初始化，请确认 VelaApplication 已在 Manifest 中注册。"
        }

    @Synchronized
    fun initialize(context: Context) {
        if (_repository != null) {
            return
        }
        val appContext = context.applicationContext
        _repository = DefaultVelaRepository(
            context = appContext,
            eventStore = EventStore(appContext),
        )
    }
}
