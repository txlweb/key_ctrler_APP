package com.idlike.kctrl.mgr

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 设置沉浸式状态栏
        setupEdgeToEdge()
        
        // 延迟启动动画，确保布局完成
        val rootLayout = findViewById<View>(R.id.rootLayout)
        rootLayout.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                rootLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                startSplashAnimation()
            }
        })
    }

    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }
    }

    private fun startSplashAnimation() {
        val expandCircle = findViewById<View>(R.id.expandCircle)
        val ivLogo = findViewById<View>(R.id.ivLogo)

        // 设置初始状态
        ivLogo.alpha = 0f
        ivLogo.scaleX = 0.8f
        ivLogo.scaleY = 0.8f
        
        // Logo淡入动画
        ivLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 红色圆形扩散动画
        expandCircle.animate()
            .scaleX(50f)
            .scaleY(50f)
            .setDuration(1500)
            .setStartDelay(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        // 延迟跳转到MainActivity
        expandCircle.postDelayed({
            navigateToMainActivity()
        }, 1000)
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        
        // 处理从通知或其他地方启动的意图
        intent.putExtras(intent)
        
        startActivity(intent)
        finish()
        
        // 添加过渡动画
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    @Deprecated("Deprecated in Java",
        ReplaceWith("super.onBackPressed()", "androidx.appcompat.app.AppCompatActivity")
    )
    override fun onBackPressed() {
        super.onBackPressed()
        // 开屏期间禁止返回键
    }
}