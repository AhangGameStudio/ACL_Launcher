package com.acl.launcher.core

import java.io.File

/**
 * JVM 入口：在电脑上直接跑自检（不需要手机）。
 * 用法：java -cp <classes> com.acl.launcher.core.SelfTestMain <输出文件>
 */
object SelfTestMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val out = try {
            SelfTest.run()
        } catch (e: Throwable) {
            "EXCEPTION=" + (e.stackTraceToString())
        }
        if (args.isNotEmpty()) {
            File(args[0]).writeText(out, Charsets.UTF_8)
        }
        println(out)
    }
}
